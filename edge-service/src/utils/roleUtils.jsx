import React from 'react';
import { Badge } from 'react-bootstrap';

export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  ADMIN: 'ADMIN',
  USER: 'USER'
};

export const AUTH_TYPES = {
  SSO: 'SSO',
  LOCAL: 'LOCAL'
};

export const isSuperAdmin = (user) => {
  return user?.roles?.includes(ROLES.SUPER_ADMIN);
};

export const hasAdminAccess = (user, currentTeam) => {
  if (!user || !currentTeam) return false;

  if (isSuperAdmin(user)) return true;

  // Check if user is ADMIN in the current team
  return currentTeam.members?.some(
    member => member.username === user.username && member.role === ROLES.ADMIN
  );
};

export const RoleBadge = ({ user }) => {
  if (!user?.roles || !Array.isArray(user.roles)) return null;
  
  // Get the highest role (SUPER_ADMIN > ADMIN > USER)
  const roles = user.roles;
  if (roles.includes(ROLES.SUPER_ADMIN)) {
    return <Badge bg="danger">SUPER ADMIN</Badge>;
  } else if (roles.includes(ROLES.ADMIN)) {
    return <Badge bg="warning" text="dark">ADMIN</Badge>;
  } else if (roles.includes(ROLES.USER)) {
    return <Badge bg="secondary">USER</Badge>;
  }
  
  return null;
};

export const AuthBadge = ({ authType }) => {
  if (!authType || !AUTH_TYPES[authType]) return null;

  return (
    <>
      <Badge bg={authType === AUTH_TYPES.SSO ? "primary" : "secondary"}>
        {authType}
      </Badge>
      <span>Authentication</span>
    </>
  );
}; 