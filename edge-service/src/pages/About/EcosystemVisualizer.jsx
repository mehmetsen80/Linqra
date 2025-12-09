import React from 'react';
import './visualizer.css';

const EcosystemVisualizer = () => {
    return (
        <div className="ecosystem-container">
            {/* Central Core */}
            <div className="core-glow"></div>
            <div className="core-hub">

                <div className="core-label">Linqra<br />Orchestrator</div>
            </div>

            {/* Orbiting Nodes */}
            <div className="node node-apps">
                <div className="node-icon"><i className="fa-solid fa-layer-group"></i></div>
                <div className="node-label">Apps</div>
            </div>

            <div className="node node-agents">
                <div className="node-icon"><i className="fa-solid fa-robot"></i></div>
                <div className="node-label">Agents</div>
            </div>

            <div className="node node-security">
                <div className="node-icon"><i className="fa-solid fa-lock"></i></div>
                <div className="node-label">Security</div>
            </div>

            <div className="node node-resiliency">
                <div className="node-icon"><i className="fa-solid fa-heart-pulse"></i></div>
                <div className="node-label">Resiliency</div>
            </div>

            <div className="node node-compliance">
                <div className="node-icon"><i className="fa-solid fa-file-contract"></i></div>
                <div className="node-label">Compliance</div>
            </div>

            {/* Connection Lines (SVG) */}
            <svg className="connections-svg">
                {/* Lines from Center to Nodes. Coordinates will be handled in CSS/JS or hardcoded for the static layout */}
                <line x1="50%" y1="50%" x2="15%" y2="30%" className="connection-line" /> {/* Apps */}
                <line x1="50%" y1="50%" x2="15%" y2="70%" className="connection-line" /> {/* Agents */}
                <line x1="50%" y1="50%" x2="85%" y2="20%" className="connection-line" /> {/* Security */}
                <line x1="50%" y1="50%" x2="85%" y2="50%" className="connection-line" /> {/* Resiliency */}
                <line x1="50%" y1="50%" x2="85%" y2="80%" className="connection-line" /> {/* Compliance */}
            </svg>
        </div>
    );
};

export default EcosystemVisualizer;
