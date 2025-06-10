import React from 'react';
import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import Login from '../pages/Login';
import Register from '../pages/Register';
import Callback from '../pages/Callback';
import Dashboard from '../pages/Dashboard';
import Metrics from '../pages/Metrics';
import ServiceStatus from '../pages/ServiceStatus';
import Alerts from '../pages/Alerts';
import Teams from '../pages/Teams';
import AdminLayout from '../layouts/AdminLayout/index';
import ProtectedRoute from '../components/common/ProtectedRoute';
import Organizations from '../pages/Organizations';
import ApiRoutes from '../pages/ApiRoutes';
import ViewRoute from '../pages/ApiRoutes/ViewRoute';
import Home from '../pages/Home';
import ViewToken from '../pages/ViewToken';
import NonProdGuard from '../components/guards/NonProdGuard';
import NotFound from '../components/common/NotFound';
import AdminGuard from '../components/guards/AdminGuard';
import Workflows from '../pages/Workflows';
import EditWorkflow from '../pages/Workflows/EditWorkflow';
import LinqProtocol from '../pages/LinqProtocol';
import Basics from '../pages/LinqProtocol/Basics';
import RequestStructure from '../pages/LinqProtocol/Basics/RequestStructure';
import ResponseFormat from '../pages/LinqProtocol/Basics/ResponseFormat';
import ErrorHandling from '../pages/LinqProtocol/Basics/ErrorHandling';
import WorkflowOverview from '../pages/LinqProtocol/Workflow';
import WorkflowCreate from '../pages/LinqProtocol/Workflow/Create';
import WorkflowExecute from '../pages/LinqProtocol/Workflow/Execute';
import WorkflowExamples from '../pages/LinqProtocol/Workflow/Examples';

const AppRoutes = () => {
  return (
    <Routes>
      {/* Public Routes */}
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/callback" element={<Callback />} />
      
      {/* Linq Protocol Routes */}
      <Route path="/linq-protocol" element={<LinqProtocol />}>
        <Route index element={<Navigate to="/linq-protocol/basics" replace />} />
        <Route path="basics" element={<Basics />} />
        <Route path="basics/request-structure" element={<RequestStructure />} />
        <Route path="basics/response-format" element={<ResponseFormat />} />
        <Route path="basics/error-handling" element={<ErrorHandling />} />
        
        {/* Workflow Routes */}
        <Route path="workflow" element={<WorkflowOverview />} />
        <Route path="workflow/create" element={<WorkflowCreate />} />
        <Route path="workflow/execute" element={<WorkflowExecute />} />
        <Route path="workflow/examples" element={<WorkflowExamples />} />
      </Route>

      {/* Protected Routes - All under AdminLayout */}
      <Route
        element={
          <ProtectedRoute>
            <AdminLayout>
              <Outlet />
            </AdminLayout>
          </ProtectedRoute>
        }
      >
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/metrics" element={<Metrics />} />
        <Route path="/service-status" element={<ServiceStatus />} />
        <Route path="/workflows" element={<Workflows />} />
        <Route path="/workflows/:workflowId/edit" element={<EditWorkflow />} />
        
        <Route 
          path="/teams" 
          element={
            <AdminGuard>
              <Teams />
            </AdminGuard>
          } 
        />
        <Route 
          path="/organizations" 
          element={
            <AdminGuard>
              <Organizations />
            </AdminGuard>
          } 
        />
        <Route path="/api-routes" element={<ApiRoutes />} />
        <Route path="/api-routes/:routeId" element={<ViewRoute />} />
        <Route 
          path="/view-token" 
          element={
            <NonProdGuard>
              <ViewToken />
            </NonProdGuard>
          } 
        />
        <Route path="/404" element={<NotFound />} />
        <Route path="*" element={<Navigate to="/404" replace />} />
      </Route>
    </Routes>
  );
};

export default AppRoutes; 