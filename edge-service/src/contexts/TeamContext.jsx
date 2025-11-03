import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from './AuthContext';
import { teamService } from '../services/teamService';
import authService from '../services/authService';
import { isSuperAdmin } from '../utils/roleUtils';

// Create the context
const TeamContext = createContext(null);

// Export the hook separately before the provider!
export const useTeam = () => {
  const context = useContext(TeamContext);
  if (!context) {
    throw new Error('useTeam must be used within a TeamProvider');
  }
  return context;
};

// Export the provider component
export const TeamProvider = ({ children }) => {
  const { user, isAuthenticated } = useAuth();
  const [teams, setTeams] = useState([]);
  const [userTeams, setUserTeams] = useState([]);
  const [currentTeam, setCurrentTeam] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchAllTeams = useCallback(async () => {
    if (!isAuthenticated) return;
    
    try {
      const response = await teamService.getAllTeams();
     //console.log('fetchAllTeams response:', response);
      if (response.success) {
        //console.log('Setting teams with:', response.data);
        setTeams(response.data || []);
      }
    } catch (error) {
      console.error('Error fetching all teams:', error);
    }
  }, [isAuthenticated]);

  const fetchUserTeams = useCallback(async () => {
    if (!isAuthenticated) {
      setUserTeams([]);
      setCurrentTeam(null);
      setLoading(false);
      return;
    }

    try {
      // console.log('Fetching user teams...'); // Debug log
      const response = await teamService.getUserTeams();
      // console.log('fetchUserTeams response:', response);
      const teams = response.data || [];
      setUserTeams(teams);
      
      if (teams.length > 0) {
        const savedTeamId = localStorage.getItem('currentTeamId');
        let teamToSet;

        if (savedTeamId && teams.some(team => team.id === savedTeamId)) {
          teamToSet = teams.find(t => t.id === savedTeamId);
        } else {
          // Sort teams by lastActiveAt (most recent first) and select the most recently used team
          const sortedTeams = teams.sort((a, b) => {
            // Handle MongoDB array date format: [year, month, day, hour, minute, second, nanoseconds]
            const parseMongoDate = (dateArray) => {
              if (!dateArray || !Array.isArray(dateArray)) return new Date(0);
              const [year, month, day, hour = 0, minute = 0, second = 0, nanoseconds = 0] = dateArray;
              return new Date(year, month - 1, day, hour, minute, second, Math.floor(nanoseconds / 1000000));
            };
            
            const aLastActive = parseMongoDate(a.lastActiveAt);
            const bLastActive = parseMongoDate(b.lastActiveAt);
            
            console.log('Team A:', a.name, 'lastActiveAt:', aLastActive.toISOString());
            console.log('Team B:', b.name, 'lastActiveAt:', bLastActive.toISOString());
            
            return bLastActive - aLastActive; // Most recent first
          });
          console.log('Sorted teams:', sortedTeams);
          teamToSet = sortedTeams[0];
          localStorage.setItem('currentTeamId', sortedTeams[0].id);
        }

        // console.log('Setting current team to:', teamToSet); // Debug log
        setCurrentTeam(teamToSet);
      }
    } catch (error) {
      console.error('Failed to fetch user teams:', error);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    console.log('Auth state changed:', isAuthenticated); // Debug log
    if (isAuthenticated) {
      fetchUserTeams();
    } else {
      // If not authenticated, set loading to false immediately
      setLoading(false);
    }
  }, [isAuthenticated, fetchUserTeams]);

  useEffect(() => {
    // console.log('Effect running, user:', user);
    // console.log('isAuthenticated:', isAuthenticated);
    if (isAuthenticated && user && isSuperAdmin(user)) {
      // console.log('Calling fetchAllTeams');
      fetchAllTeams();
    }
  }, [isAuthenticated, user, fetchAllTeams]);

  // Add a debug effect to monitor state changes
  useEffect(() => {
    // console.log('Current team state:', currentTeam);
    // console.log('User teams state:', userTeams);
  }, [currentTeam, userTeams]);

  const switchTeam = useCallback(async (teamId) => {
    const team = userTeams.find(t => t.id === teamId);
    if (team && user?.username) {
      try {
        console.log('🔄 Switching to team:', teamId, 'team name:', team.name);
        
        // Call backend to switch team and get new JWT token
        const result = await authService.switchTeam(user.username, teamId);
        
        if (result.data) {
          console.log('✅ JWT token updated successfully');
          
          // Update the last active timestamp in the backend
          try {
            const updateResult = await teamService.updateLastActiveAt(teamId);
            console.log('updateLastActiveAt result:', updateResult);
            if (updateResult.success) {
              console.log('Successfully updated last active timestamp for team:', teamId);
            } else {
              console.warn('Failed to update last active timestamp:', updateResult.error);
            }
          } catch (error) {
            console.error('Error updating last active timestamp:', error);
          }
          
          // Update local state and storage
          setCurrentTeam(team);
          localStorage.setItem('currentTeamId', teamId);
          
          console.log('✅ Successfully switched team and updated JWT token');
        } else {
          console.error('❌ Failed to switch team:', result.error);
          throw new Error(result.error || 'Failed to switch team');
        }
      } catch (error) {
        console.error('❌ Error switching team:', error);
        throw error;
      }
    }
  }, [userTeams, user?.username]);

  const hasPermission = useCallback((routeId, permission) => {
    if (!currentTeam) return false;
    const route = currentTeam.routes?.find(r => r.routeId === routeId);
    return route?.permissions?.includes(permission) || false;
  }, [currentTeam]);

  const value = useMemo(() => ({
    currentTeam,
    userTeams,
    teams,
    loading,
    switchTeam,
    hasPermission,
    refreshTeams: fetchUserTeams,
    fetchAllTeams
  }), [currentTeam, userTeams, teams, loading, switchTeam, hasPermission, fetchUserTeams, fetchAllTeams]);

  return (
    <TeamContext.Provider value={value}>
      {children}
    </TeamContext.Provider>
  );
};
