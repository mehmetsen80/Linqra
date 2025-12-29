import React from 'react';
import { HiArrowLeft, HiChatAlt, HiCog, HiLogout, HiDocumentText } from 'react-icons/hi';
import { useNavigate } from 'react-router-dom';
import { Button } from 'react-bootstrap';

const AssistantSidebar = ({ activeMode = 'chat' }) => {
    const navigate = useNavigate();

    return (
        <div className="d-flex flex-column align-items-center bg-dark text-white py-3" style={{ width: '60px', height: '100vh', position: 'fixed', left: 0, top: 0, borderRight: '1px solid #333', zIndex: 1000 }}>
            <Button variant="link" className="text-white p-2 mb-3" onClick={() => navigate(-1)} title="Back">
                <HiArrowLeft size={24} />
            </Button>

            <Button variant="link" className={`p-2 mb-3 ${activeMode === 'chat' ? 'text-info' : 'text-secondary'}`} title="Chat">
                <HiChatAlt size={24} />
            </Button>

            {/* Placeholder for future modes like 'code' or 'history' if we want them as separate sidebar items */}
            {/* 
            <Button variant="link" className={`p-2 mb-3 ${activeMode === 'document' ? 'text-info' : 'text-secondary'}`} title="Documents">
                <HiDocumentText size={24} />
            </Button>
            */}

            <div className="mt-auto">
                <Button variant="link" className="text-secondary p-2 mb-2" title="Settings">
                    <HiCog size={24} />
                </Button>
                {/* 
                <Button variant="link" className="text-danger p-2" title="Logout/Exit">
                    <HiLogout size={24} />
                </Button> 
                */}
            </div>
        </div>
    );
};

export default AssistantSidebar;
