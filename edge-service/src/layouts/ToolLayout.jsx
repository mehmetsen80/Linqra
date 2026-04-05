import React from 'react';
import { useAuth } from '../contexts/AuthContext';
import { Outlet } from 'react-router-dom';
import Header from '../components/common/Header';
import './AdminLayout/styles.css';

const ToolLayout = () => {
    const { user } = useAuth();

    // ToolLayout is a hybrid layout. 
    // It provides the main menu/header for everyone, 
    // but doesn't force a login redirect like AdminLayout does.
    return (
        <div className="admin-layout">
            <Header />
            <main className="main-content">
                <Outlet />
            </main>
        </div>
    );
};

export default ToolLayout;
