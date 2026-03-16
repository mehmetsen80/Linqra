import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Modal, Button, OverlayTrigger, Tooltip } from 'react-bootstrap';
import html2canvas from 'html2canvas';

import './StepDescriptions/styles.css';

const WorkflowGraphModal = ({ show, onHide, workflowData, onSave, agentTask }) => {
    const [steps, setSteps] = useState([]);
    const [lines, setLines] = useState([]);
    const [zoom, setZoom] = useState(1);
    const [selectedStep, setSelectedStep] = useState(null);
    const [toolbarPos, setToolbarPos] = useState({ x: null, y: 20 }); // null x = centered
    const containerRef = useRef(null);
    const scrollContainerRef = useRef(null);
    const nodeRefs = useRef({});
    const stepJsonRefs = useRef({});
    const toolbarRef = useRef(null);
    const isDraggingToolbar = useRef(false);
    const toolbarDragStart = useRef({ mouseX: 0, mouseY: 0, elemX: 0, elemY: 0 });

    // Canvas Panning State
    const isDraggingCanvas = useRef(false);
    const startX = useRef(0);
    const startY = useRef(0);
    const scrollLeft = useRef(0);
    const scrollTop = useRef(0);

    useEffect(() => {
        if (show && workflowData?.workflow) {
            setSteps(JSON.parse(JSON.stringify(workflowData.workflow)));
        } else if (show && workflowData?.query?.workflow) {
            setSteps(JSON.parse(JSON.stringify(workflowData.query.workflow)));
        } else {
            setSteps([]);
        }
    }, [show, workflowData]);

    const setRef = (stepId, el) => {
        if (el) {
            nodeRefs.current[stepId] = el;
        } else {
            delete nodeRefs.current[stepId];
        }
    };

    const calculateLines = useCallback(() => {
        if (!containerRef.current || steps.length === 0) return;
        const newLines = [];

        // 1. Map out all logical connection points
        const connectionPoints = [];
        connectionPoints.push({ id: 'start', type: 'terminal' });

        steps.forEach((step) => {
            connectionPoints.push({ id: step.step, type: 'step', data: step });
            if (step.jump) {
                connectionPoints.push({ id: `dec-${step.step}`, type: 'decision', data: step });
            }
        });

        connectionPoints.push({ id: 'end', type: 'terminal' });

        // 2. Draw sequential connections (Main track)
        for (let i = 0; i < connectionPoints.length - 1; i++) {
            const current = connectionPoints[i];
            const next = connectionPoints[i + 1];

            // Skip terminal decorative segments (START->Step and Step->END) as they use icons
            if (current.id === 'start' || next.id === 'end') continue;

            const currentEl = nodeRefs.current[current.id];
            const nextEl = nodeRefs.current[next.id];

            if (currentEl && nextEl) {
                const startX = currentEl.offsetLeft + currentEl.offsetWidth;
                const startY = currentEl.offsetTop + currentEl.offsetHeight / 2;
                const endX = nextEl.offsetLeft;
                const endY = nextEl.offsetTop + nextEl.offsetHeight / 2;

                const midX = (startX + endX) / 2;
                const midY = (startY + endY) / 2;

                newLines.push({
                    id: `seq-${current.id}-${next.id}`,
                    path: `M ${startX} ${startY} L ${endX} ${endY}`,
                    type: 'sequential',
                    label: current.type === 'decision' ? 'YES' : null,
                    labelX: midX - 10,
                    labelY: midY - 10
                });
            }
        }

        // 3. Draw Jump/Branch (NO) connections
        steps.forEach((step, index) => {
            if (step.jump) {
                const decEl = nodeRefs.current[`dec-${step.step}`];
                if (!decEl) return;

                const decWidth = decEl.offsetWidth;
                const decHeight = decEl.offsetHeight;
                const decLeft = decEl.offsetLeft;
                const decTop = decEl.offsetTop;

                if (step.jump.targetStep > 0) {
                    const targetEl = nodeRefs.current[step.jump.targetStep];
                    if (targetEl) {
                        const targetLeft = targetEl.offsetLeft;
                        const targetTop = targetEl.offsetTop;
                        const targetWidth = targetEl.offsetWidth;

                        const startX = decLeft + decWidth / 2;
                        const startY = decTop; // Branch UP
                        const endX = targetLeft + targetWidth / 2;
                        const endY = targetTop - 10;

                        const midX = (startX + endX) / 2;
                        const distance = Math.abs(index - steps.findIndex(s => s.step === step.jump.targetStep));
                        const height = 60 + (distance * 20);
                        const controlY = Math.min(startY, endY) - height;

                        newLines.push({
                            id: `jump-${step.step}-${step.jump.targetStep}`,
                            path: `M ${startX} ${startY} L ${startX} ${controlY} L ${endX} ${controlY} L ${endX} ${endY}`,
                            type: 'jump',
                            condition: step.jump.condition,
                            labelX: midX,
                            labelY: controlY - 22
                        });
                    }
                } else if (step.jump.targetStep === 0) {
                    // Terminal branch
                    const startX = decLeft + decWidth / 2;
                    const startY = decTop + decHeight; // Branch DOWN
                    const terminalY = startY + 80;

                    newLines.push({
                        id: `jump-${step.step}-term`,
                        path: `M ${startX} ${startY} L ${startX} ${terminalY}`,
                        type: 'terminal',
                        condition: step.jump.condition,
                        labelX: startX,
                        labelY: terminalY + 45, // Moved further down to make room for STOP
                        terminalX: startX,
                        terminalY: terminalY
                    });
                }
            }
        });
        setLines(newLines);
    }, [steps, show]);

    useEffect(() => {
        if (show) {
            const timer1 = setTimeout(calculateLines, 100);
            const timer2 = setTimeout(calculateLines, 500);
            window.addEventListener('resize', calculateLines);

            // Scroll container observer in case layout shifts
            if (scrollContainerRef.current) {
                scrollContainerRef.current.addEventListener('scroll', calculateLines);
            }

            return () => {
                clearTimeout(timer1);
                clearTimeout(timer2);
                window.removeEventListener('resize', calculateLines);
                if (scrollContainerRef.current) {
                    scrollContainerRef.current.removeEventListener('scroll', calculateLines);
                }
            };
        }
    }, [show, steps, calculateLines]);

    const handleDragStart = (e, index) => {
        // Prevent canvas drag if we grab a node
        e.stopPropagation();
        e.dataTransfer.setData('stepIndex', index.toString());
        e.dataTransfer.effectAllowed = 'move';
        setTimeout(calculateLines, 50);
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
    };

    const handleDrop = (e, targetIndex) => {
        e.preventDefault();
        const sourceIndexStr = e.dataTransfer.getData('stepIndex');
        if (sourceIndexStr === null || sourceIndexStr === '') return;

        const srcIdx = parseInt(sourceIndexStr, 10);
        if (srcIdx === targetIndex) return;

        const newSteps = [...steps];
        const [movedStep] = newSteps.splice(srcIdx, 1);
        newSteps.splice(targetIndex, 0, movedStep);

        setSteps(newSteps);
    };

    const handleSaveContext = () => {
        if (onSave && workflowData) {
            let updatedData = JSON.parse(JSON.stringify(workflowData));
            if (updatedData.workflow) {
                updatedData.workflow = steps;
            } else if (updatedData.query?.workflow) {
                updatedData.query.workflow = steps;
            }
            onSave(updatedData);
        }
        onHide();
    };

    const handleSnapshot = async () => {
        if (!containerRef.current) return;

        const target = containerRef.current;
        const originalTransform = target.style.transform;

        try {
            // Strip zoom so html2canvas measures real pixel dimensions
            target.style.transform = 'scale(1)';

            // Wait for browser reflow before measuring
            await new Promise((resolve) => setTimeout(resolve, 80));

            const captureWidth = target.scrollWidth;
            const captureHeight = target.scrollHeight;

            // Capture the content (nodes, SVG lines, labels) without background
            const contentCanvas = await html2canvas(target, {
                scale: 2,
                backgroundColor: null, // transparent so we can composite our own bg
                useCORS: true,
                allowTaint: true,
                logging: false,
                width: captureWidth,
                height: captureHeight,
                windowWidth: captureWidth,
                windowHeight: captureHeight
            });

            // Restore live DOM transform
            target.style.transform = originalTransform;

            // Composite: create final canvas with grid background + content on top
            const finalCanvas = document.createElement('canvas');
            finalCanvas.width = contentCanvas.width;
            finalCanvas.height = contentCanvas.height;
            const ctx = finalCanvas.getContext('2d');

            // 1. Fill background
            ctx.fillStyle = '#f8f9fa';
            ctx.fillRect(0, 0, finalCanvas.width, finalCanvas.height);

            // 2. Draw dot grid (40px grid × scale 2 = 80px in canvas pixels)
            const gridSize = 80;
            const dotRadius = 2;
            ctx.fillStyle = '#201f1f53';
            for (let x = 0; x <= finalCanvas.width; x += gridSize) {
                for (let y = 0; y <= finalCanvas.height; y += gridSize) {
                    ctx.beginPath();
                    ctx.arc(x, y, dotRadius, 0, Math.PI * 2);
                    ctx.fill();
                }
            }

            // 3. Draw html2canvas content on top
            ctx.drawImage(contentCanvas, 0, 0);

            const image = finalCanvas.toDataURL('image/png');
            const link = document.createElement('a');
            link.href = image;
            link.download = `${agentTask?.name || 'workflow'}-snapshot-${new Date().toISOString().slice(0, 10)}.png`;
            link.click();
        } catch (error) {
            console.error('Failed to export snapshot:', error);
            target.style.transform = originalTransform;
        }
    };

    const getStepColor = (target, intent) => {
        if (target === 'api-gateway' && intent && intent.startsWith('/api/milvus/')) {
            if (intent.includes('/search')) return '#17a2b8';
            if (intent.includes('/records')) return '#28a745';
        }
        const aiServiceColors = {
            'openai': '#28a745', 'gemini': '#007bff', 'anthropic': '#ff6b35',
            'huggingface': '#ffcc02', 'cohere': '#39c5bb'
        };
        const coreServiceColors = {
            'api-gateway': '#6c757d', 'auth-service': '#dc3545', 'notification-service': '#ffc107'
        };
        if (aiServiceColors[target]) return aiServiceColors[target];
        if (coreServiceColors[target]) return coreServiceColors[target];

        const colors = ['#6f42c1', '#e83e8c', '#fd7e14', '#20c997', '#6610f2', '#d63384', '#198754', '#0d6efd'];
        let hash = 0;
        for (let i = 0; i < target.length; i++) hash = target.charCodeAt(i) + ((hash << 5) - hash);
        return colors[Math.abs(hash) % colors.length];
    };

    const getStepIcon = (target, action, intent) => {
        if (target === 'api-gateway' && intent && intent.startsWith('/api/milvus/')) {
            if (intent.includes('/search')) return 'fas fa-search';
            if (intent.includes('/records')) return 'fas fa-save';
        }
        const iconMap = { 'quotes-service': 'fas fa-user-friends', 'api-gateway': 'fas fa-database', 'openai': 'fas fa-robot', 'gemini': 'fas fa-brain', 'milvus': 'fas fa-vector-square' };
        const actionIconMap = { 'fetch': 'fas fa-download', 'create': 'fas fa-plus-circle', 'generate': 'fas fa-magic', 'search': 'fas fa-search' };
        return iconMap[target] || actionIconMap[action] || 'fas fa-cog';
    };

    const getStepLabel = (target, action, intent) => {
        if (target === 'api-gateway' && intent && intent.startsWith('/api/milvus/')) {
            if (intent.includes('/search')) return { target: 'milvus-search', action: 'search' };
            if (intent.includes('/records')) return { target: 'milvus-store', action: 'save' };
        }
        return { target: target, action: action };
    };

    const handleCanvasPointerDown = (e) => {
        if (e.target.closest('.step-box') || e.target.closest('.btn')) {
            return;
        }

        isDraggingCanvas.current = true;
        startX.current = e.clientX;
        startY.current = e.clientY;
        scrollLeft.current = scrollContainerRef.current.scrollLeft;
        scrollTop.current = scrollContainerRef.current.scrollTop;

        // Capture pointer events so dragging works even if we leave the element
        e.target.setPointerCapture(e.pointerId);
        scrollContainerRef.current.style.cursor = 'grabbing';
    };

    const handleCanvasPointerUp = (e) => {
        isDraggingCanvas.current = false;
        e.target.releasePointerCapture(e.pointerId);
        if (scrollContainerRef.current) scrollContainerRef.current.style.cursor = 'grab';
    };

    const handleCanvasPointerMove = (e) => {
        if (!isDraggingCanvas.current || !scrollContainerRef.current) return;

        const x = e.clientX;
        const y = e.clientY;
        const walkX = (x - startX.current);
        const walkY = (y - startY.current);

        scrollContainerRef.current.scrollLeft = scrollLeft.current - walkX;
        scrollContainerRef.current.scrollTop = scrollTop.current - walkY;
    };

    if (!show) return null;

    return (
        <Modal show={show} onHide={onHide} size="xl" fullscreen centered>
            <style>
                {`
                .workflow-modal-header .btn-close {
                    filter: invert(1) grayscale(100%) brightness(0) !important;
                    opacity: 0.8;
                }
                .step-box-custom-tooltip {
                    visibility: hidden;
                    opacity: 0;
                    position: absolute;
                    bottom: 100%;
                    left: 50%;
                    transform: translateX(-50%);
                    background-color: #212529;
                    color: #fff;
                    padding: 12px;
                    border-radius: 8px;
                    z-index: 1000;
                    width: max-content;
                    max-width: 300px;
                    text-align: left;
                    box-shadow: 0 4px 15px rgba(0,0,0,0.2);
                    pointer-events: none;
                    margin-bottom: 15px;
                    transition: opacity 0.2s ease, visibility 0.2s ease;
                }
                .step-box-custom-tooltip::after {
                    content: "";
                    position: absolute;
                    top: 100%;
                    left: 50%;
                    margin-left: -8px;
                    border-width: 8px;
                    border-style: solid;
                    border-color: #212529 transparent transparent transparent;
                }
                .step-box:hover {
                    z-index: 1000; /* Pop above SVG lines on hover */
                }
                .step-box:hover .step-box-custom-tooltip {
                    visibility: visible;
                    opacity: 1;
                }
                .decision-diamond {
                    width: 100px;
                    height: 100px;
                    position: relative;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    flex-shrink: 0;
                    margin: 0;
                    transition: all 0.2s;
                    z-index: 5;
                }
                .decision-diamond:hover {
                    transform: scale(1.05);
                    background-color: #e67200;
                    cursor: pointer;
                    z-index: 10;
                }
                .decision-condition {
                    color: white;
                    font-size: 0.48rem;
                    line-height: 1.1;
                    font-weight: 800;
                    text-align: center;
                    word-break: break-all;
                    max-width: 65px;
                    position: absolute;
                    z-index: 2;
                    display: -webkit-box;
                    WebkitLineClamp: 4;
                    WebkitBoxOrient: 'vertical';
                    overflow: hidden;
                    text-transform: uppercase;
                }
                `}
            </style>
            <Modal.Header closeButton className="bg-light border-bottom workflow-modal-header">
                <Modal.Title className="d-flex align-items-center gap-3 text-dark">
                    <i className="fas fa-robot" style={{ color: 'var(--primary-color)' }}></i>
                    <span style={{ color: 'black' }}>{agentTask?.name || 'Workflow Diagram'}</span>
                </Modal.Title>
            </Modal.Header>
            <Modal.Body className="bg-light p-0 position-relative" style={{ overflow: 'hidden', display: 'flex', flexDirection: 'row', minHeight: '100vh', flex: 1 }}>
                <div
                    ref={scrollContainerRef}
                    className="step-descriptions-container position-relative m-0 border-0 shadow-none h-100 w-100 rounded-0"
                    onPointerDown={handleCanvasPointerDown}
                    onPointerUp={handleCanvasPointerUp}
                    onPointerCancel={handleCanvasPointerUp}
                    onPointerMove={handleCanvasPointerMove}
                    style={{
                        flex: '1 1 auto',
                        height: '100%',
                        overflowX: 'auto',
                        overflowY: 'auto',
                        display: 'flex',
                        flexDirection: 'column',
                        cursor: 'grab',
                        userSelect: 'none', // Prevent text highlighting while dragging canvas
                        touchAction: 'none', // Prevent browser scrolling issues on trackpads/touch
                        position: 'relative'
                    }}
                >
                    {/* Explicit Infinite Grid Background */}
                    <div style={{
                        position: 'absolute',
                        top: 0, left: 0,
                        width: 'max(100%, 3000px)',
                        height: 'max(100%, 3000px)', // Guarantees grid is huge
                        backgroundColor: '#f8f9fa',
                        backgroundImage: 'radial-gradient(#ced4da 2px, transparent 2px)',
                        backgroundSize: '40px 40px',
                        backgroundPosition: '0 0',
                        zIndex: 0,
                        pointerEvents: 'none' // Clicks go straight through to scroll container
                    }} />
                    <div
                        ref={containerRef}
                        data-snapshot-target="true"
                        style={{
                            display: 'flex',
                            flexDirection: 'column',
                            minWidth: 'max-content',
                            minHeight: 'max-content',
                            margin: 'auto',
                            transform: `scale(${zoom})`,
                            transformOrigin: 'top center',
                            transition: 'transform 0.2s ease-in-out'
                        }}
                    >
                        <div
                            style={{
                                display: 'flex',
                                flexDirection: 'row',
                                gap: '50px',
                                zIndex: 2,
                                position: 'relative',
                                minWidth: 'max-content',
                                justifyContent: 'center',
                                alignItems: 'center',
                                padding: '350px 80px 300px 80px' // Padding for branches above/below
                            }}
                        >
                            {/* SVG Connector Overlay */}
                            <svg
                                style={{
                                    position: 'absolute',
                                    top: 0, left: 0,
                                    width: '100%',
                                    height: '100%',
                                    pointerEvents: 'none',
                                    zIndex: 1,
                                    overflow: 'visible'
                                }}
                            >
                                <defs>
                                    <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                                        <polygon points="0 0, 10 3.5, 0 7" fill="#adb5bd" />
                                    </marker>
                                    <marker id="arrowhead-jump" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                                        <polygon points="0 0, 10 3.5, 0 7" fill="#ffc107" />
                                    </marker>
                                </defs>
                                {lines.map((line) => (
                                    <g key={line.id}>
                                        <path
                                            d={line.path}
                                            fill="none"
                                            stroke={line.type === 'jump' || line.type === 'terminal' ? '#ffc107' : '#adb5bd'}
                                            strokeWidth={line.type === 'jump' ? 3 : 2}
                                            strokeDasharray={line.type === 'jump' ? '5,5' : 'none'}
                                            markerEnd={line.type !== 'terminal' ? (line.type === 'jump' ? "url(#arrowhead-jump)" : "url(#arrowhead)") : "none"}
                                        />
                                        {line.type === 'terminal' && (
                                            <>
                                                <circle cx={line.terminalX} cy={line.terminalY} r="8" fill="#dc3545" />
                                                <text
                                                    x={line.terminalX}
                                                    y={line.terminalY + 22}
                                                    fill="#dc3545"
                                                    fontSize="10"
                                                    fontWeight="800"
                                                    textAnchor="middle"
                                                    style={{ textShadow: '1px 1px 2px white, -1px -1px 2px white' }}
                                                >
                                                    STOP
                                                </text>
                                            </>
                                        )}
                                        {line.label && (
                                            <text
                                                x={line.labelX}
                                                y={line.labelY}
                                                fill={line.type === 'sequential' ? '#198754' : '#dc3545'}
                                                fontSize="14"
                                                fontWeight="bold"
                                                textAnchor="middle"
                                                style={{ textShadow: '1px 1px 2px white, -1px -1px 2px white', pointerEvents: 'none' }}
                                            >
                                                {line.label}
                                            </text>
                                        )}
                                        {(line.type === 'jump' || line.type === 'terminal') && (
                                            <text
                                                x={line.labelX}
                                                y={line.labelY}
                                                fill={line.type === 'terminal' ? '#dc3545' : '#fd7e14'}
                                                fontSize="14"
                                                fontWeight="bold"
                                                textAnchor="middle"
                                                style={{ textShadow: '1px 1px 2px white, -1px -1px 2px white', pointerEvents: 'none' }}
                                            >
                                                NO
                                            </text>
                                        )}
                                        {(line.type === 'jump' || line.type === 'terminal') && line.condition && (
                                            <text
                                                x={line.labelX}
                                                y={line.type === 'terminal' ? line.labelY + 24 : line.labelY - 20}
                                                fill="#6c757d"
                                                fontSize="10"
                                                fontFamily="monospace"
                                                textAnchor="middle"
                                                style={{ textShadow: '1px 1px 2px white, -1px -1px 2px white' }}
                                            >
                                                IF {line.condition}
                                            </text>
                                        )}
                                    </g>
                                ))}
                            </svg>

                            {/* START Terminal Node */}
                            <div
                                ref={(el) => setRef('start', el)}
                                style={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: '6px',
                                    width: '80px',
                                    flexShrink: 0
                                }}
                            >
                                <div style={{
                                    width: '64px',
                                    height: '64px',
                                    borderRadius: '50%',
                                    backgroundColor: '#198754',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    boxShadow: '0 0 0 4px #d1e7dd, 0 4px 12px rgba(25,135,84,0.35)',
                                }}>
                                    <span style={{ color: 'white', fontSize: '0.7rem', fontWeight: '800', letterSpacing: '1px' }}>START</span>
                                </div>
                            </div>
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                color: '#adb5bd',
                                fontSize: '1.5rem',
                                fontWeight: 'bold',
                                flexShrink: 0,
                                margin: '0 -30px'
                            }}>
                                <i className="fas fa-angle-double-right"></i>
                            </div>

                            {/* Draggable Step Nodes & Diamonds */}
                            {steps.map((step, index) => {
                                const stepLabels = getStepLabel(step.target, step.action, step.intent);
                                const stepColor = getStepColor(step.target, step.intent);
                                const stepIcon = getStepIcon(step.target, step.action, step.intent);

                                return (
                                    <React.Fragment key={`step-group-${step.step}`}>
                                        <div
                                            draggable="true"
                                            ref={(el) => setRef(step.step, el)}
                                            onDragStart={(e) => handleDragStart(e, index)}
                                            onDragOver={handleDragOver}
                                            onDrop={(e) => handleDrop(e, index)}
                                            onDragEnd={calculateLines}
                                            style={{
                                                cursor: 'grab',
                                                height: 'fit-content',
                                                width: '130px',
                                                display: 'flex',
                                                justifyContent: 'center'
                                            }}
                                        >
                                            <div
                                                className="step-box"
                                                onClick={() => {
                                                    setSelectedStep(step.step);
                                                    setTimeout(() => {
                                                        stepJsonRefs.current[step.step]?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                                    }, 50);
                                                }}
                                                style={{
                                                    '--step-color': stepColor,
                                                    width: '130px',
                                                    height: '130px',
                                                    display: 'flex',
                                                    flexDirection: 'column',
                                                    justifyContent: 'center',
                                                    alignItems: 'center',
                                                    padding: '10px',
                                                    boxShadow: step.async ? '0 0 10px rgba(13, 110, 253, 0.4)' : undefined,
                                                    borderColor: step.async ? '#0d6efd' : undefined,
                                                    margin: 0,
                                                    position: 'relative'
                                                }}
                                            >
                                                <div className="step-box-custom-tooltip">
                                                    <div className="tooltip-header mb-1">
                                                        <strong>Step {step.step}: {step.summary || step.target}</strong>
                                                    </div>
                                                    <div className="tooltip-action mt-2">
                                                        <i className={stepIcon}></i> Action: {stepLabels.action}
                                                    </div>
                                                    {step.description && <div className="tooltip-description mt-2 fst-italic">{step.description}</div>}
                                                    {step.intent && <div className="tooltip-intent mt-1 font-monospace small" style={{ wordBreak: 'break-word', whiteSpace: 'normal' }}>{step.intent}</div>}
                                                </div>
                                                <div
                                                    className="step-number"
                                                    style={{ backgroundColor: stepColor, top: '-10px', right: '-10px', position: 'absolute' }}
                                                >
                                                    {step.step}
                                                </div>
                                                <div className="step-icon">
                                                    <i className={stepIcon} style={{ fontSize: '1.8rem' }}></i>
                                                </div>
                                                <div className="step-label" style={{ minHeight: 'auto', marginTop: '3px', width: '100%' }}>
                                                    <div className="step-target text-truncate w-100 px-1" title={stepLabels.target}>{stepLabels.target}</div>
                                                    <div className="step-action">
                                                        {stepLabels.action}
                                                        {step.async && <span className="ms-1 text-primary"><i className="fas fa-bolt" title="Async"></i></span>}
                                                        {step.jump && <span className="ms-1 text-warning"><i className="fas fa-code-branch" title="Jump"></i></span>}
                                                    </div>
                                                    {step.summary && (
                                                        <div className="w-100 px-1" title={step.summary} style={{
                                                            fontSize: '0.65rem',
                                                            color: '#6c757d',
                                                            fontStyle: 'italic',
                                                            marginTop: '3px',
                                                            lineHeight: 1.2,
                                                            display: '-webkit-box',
                                                            WebkitLineClamp: 2,
                                                            WebkitBoxOrient: 'vertical',
                                                            overflow: 'hidden',
                                                            wordBreak: 'break-word',
                                                            whiteSpace: 'normal',
                                                            textAlign: 'center'
                                                        }}>
                                                            {step.summary}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </div>

                                        {step.jump && (
                                            <div
                                                key={`dec-${step.step}`}
                                                ref={(node) => setRef(`dec-${step.step}`, node)}
                                                className="decision-diamond"
                                                title={`Condition: ${step.jump.condition}`}
                                            >
                                                <svg width="100" height="100" style={{ position: 'absolute', top: 0, left: 0, zIndex: 1, filter: 'drop-shadow(0 4px 6px rgba(253, 126, 20, 0.4))' }}>
                                                    <polygon points="50,0 100,50 50,100 0,50" fill="#fd7e14" />
                                                </svg>
                                                <div className="decision-condition">
                                                    {step.jump.condition}
                                                </div>
                                            </div>
                                        )}
                                    </React.Fragment>
                                );
                            })}

                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                color: '#adb5bd',
                                fontSize: '1.5rem',
                                fontWeight: 'bold',
                                flexShrink: 0,
                                margin: '0 -30px'
                            }}>
                                <i className="fas fa-angle-double-right"></i>
                            </div>

                            {/* END Terminal Node */}
                            <div
                                ref={(el) => setRef('end', el)}
                                style={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: '6px',
                                    width: '80px',
                                    flexShrink: 0
                                }}
                            >
                                <div style={{
                                    width: '64px',
                                    height: '64px',
                                    borderRadius: '50%',
                                    backgroundColor: '#dc3545',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    boxShadow: '0 0 0 4px #f8d7da, 0 4px 12px rgba(220,53,69,0.35)',
                                }}>
                                    <span style={{ color: 'white', fontSize: '0.7rem', fontWeight: '800', letterSpacing: '1px' }}>END</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                {/* Floating Controls Overlay — draggable */}
                <div
                    ref={toolbarRef}
                    onPointerDown={(e) => {
                        // Only drag from the pill background, not from buttons inside
                        if (e.target !== toolbarRef.current) return;
                        e.stopPropagation();
                        isDraggingToolbar.current = true;
                        const rect = toolbarRef.current.getBoundingClientRect();
                        const parentRect = toolbarRef.current.offsetParent?.getBoundingClientRect() || { left: 0, top: 0 };
                        toolbarDragStart.current = {
                            mouseX: e.clientX,
                            mouseY: e.clientY,
                            elemX: rect.left - parentRect.left,
                            elemY: rect.top - parentRect.top
                        };
                        toolbarRef.current.setPointerCapture(e.pointerId);
                    }}
                    onPointerMove={(e) => {
                        if (!isDraggingToolbar.current) return;
                        e.stopPropagation();
                        const dx = e.clientX - toolbarDragStart.current.mouseX;
                        const dy = e.clientY - toolbarDragStart.current.mouseY;
                        setToolbarPos({ x: toolbarDragStart.current.elemX + dx, y: toolbarDragStart.current.elemY + dy });
                    }}
                    onPointerUp={(e) => {
                        isDraggingToolbar.current = false;
                        toolbarRef.current?.releasePointerCapture(e.pointerId);
                    }}
                    style={{
                        position: 'absolute',
                        top: toolbarPos.y,
                        ...(toolbarPos.x === null
                            ? { left: '50%', transform: 'translateX(-50%)' }
                            : { left: toolbarPos.x, transform: 'none' }),
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px',
                        backgroundColor: 'white',
                        padding: '8px 16px',
                        borderRadius: '50px',
                        boxShadow: '0 4px 15px rgba(0,0,0,0.1)',
                        zIndex: 1000,
                        border: '1px solid #dee2e6',
                        cursor: 'grab',
                        userSelect: 'none'
                    }}
                >
                    <Button variant="outline-secondary" className="rounded-circle p-0 d-flex justify-content-center align-items-center" style={{ width: '32px', height: '32px', minWidth: '32px', minHeight: '32px', flexShrink: 0 }} onClick={() => setZoom(z => Math.max(z - 0.2, 0.4))} title="Zoom Out">
                        <i className="fas fa-minus small"></i>
                    </Button>
                    <span className="small text-muted font-monospace" style={{ minWidth: '40px', textAlign: 'center', userSelect: 'none' }}>
                        {Math.round(zoom * 100)}%
                    </span>
                    <Button variant="outline-secondary" className="rounded-circle p-0 d-flex justify-content-center align-items-center" style={{ width: '32px', height: '32px', minWidth: '32px', minHeight: '32px', flexShrink: 0 }} onClick={() => setZoom(z => Math.min(z + 0.2, 2.0))} title="Zoom In">
                        <i className="fas fa-plus small"></i>
                    </Button>
                    <Button variant="outline-secondary" className="rounded-circle p-0 d-flex justify-content-center align-items-center" style={{ width: '32px', height: '32px', minWidth: '32px', minHeight: '32px', flexShrink: 0 }} onClick={() => setZoom(1.0)} title="Reset Zoom">
                        <i className="fas fa-undo small"></i>
                    </Button>

                    <div style={{ width: '1px', height: '20px', backgroundColor: '#ced4da', margin: '0 5px' }}></div>

                    <Button variant="outline-secondary" className="rounded-circle p-0 d-flex justify-content-center align-items-center me-2" style={{ width: '32px', height: '32px', minWidth: '32px', minHeight: '32px', flexShrink: 0 }} onClick={handleSnapshot} title="Download Snapshot (PNG)">
                        <i className="fas fa-camera small"></i>
                    </Button>

                    <Button variant="outline-danger" className="rounded-pill px-3" size="sm" onClick={onHide}>Close</Button>
                </div>

                {/* JSON Inspector Panel */}
                {
                    selectedStep !== null && (() => {
                        const fullJson = JSON.stringify(workflowData, null, 2);
                        const workflowSteps = workflowData?.workflow || workflowData?.query?.workflow || [];

                        return (
                            <div style={{
                                width: '380px',
                                minWidth: '380px',
                                height: '100%',
                                borderLeft: '1px solid #dee2e6',
                                backgroundColor: '#1e1e2e',
                                display: 'flex',
                                flexDirection: 'column',
                                overflow: 'hidden',
                                flexShrink: 0,
                                boxShadow: '-4px 0 16px rgba(0,0,0,0.15)'
                            }}>
                                {/* Panel Header */}
                                <div style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    padding: '10px 14px',
                                    backgroundColor: '#13131f',
                                    borderBottom: '1px solid #2d2d44',
                                    flexShrink: 0
                                }}>
                                    <span style={{ color: '#a9b1d6', fontSize: '0.8rem', fontFamily: 'monospace', fontWeight: 600 }}>
                                        <i className="fas fa-code me-2" style={{ color: 'var(--primary-color)' }}></i>
                                        workflow.json — Step {selectedStep}
                                    </span>
                                    <button onClick={() => setSelectedStep(null)} style={{
                                        background: 'none', border: 'none', color: '#6c757d', cursor: 'pointer', fontSize: '1rem', lineHeight: 1, padding: '2px 6px'
                                    }} title="Close Inspector">✕</button>
                                </div>

                                {/* Scrollable JSON body */}
                                <div style={{ overflowY: 'auto', flex: 1, padding: '12px 0' }}>
                                    {/* Top-level opening */}
                                    <pre style={{ margin: 0, padding: '0 14px 4px', color: '#a9b1d6', fontSize: '0.72rem', fontFamily: 'monospace', lineHeight: 1.7 }}>
                                        {`{\n  "link": ${JSON.stringify(workflowData?.link || workflowData?.query?.link, null, 2).replace(/\n/g, '\n  ')},\n  "query": {\n    "intent": ${JSON.stringify(workflowData?.query?.intent)},\n    "params": ${JSON.stringify(workflowData?.query?.params, null, 2).replace(/\n/g, '\n    ')},\n    "workflow": [`}
                                    </pre>

                                    {/* Each step block */}
                                    {workflowSteps.map((step, idx) => {
                                        const isSelected = step.step === selectedStep;
                                        return (
                                            <div
                                                key={step.step}
                                                ref={(el) => { stepJsonRefs.current[step.step] = el; }}
                                                onClick={() => setSelectedStep(step.step)}
                                                style={{
                                                    margin: '2px 8px',
                                                    borderRadius: '6px',
                                                    backgroundColor: isSelected ? 'rgba(122, 162, 247, 0.15)' : 'transparent',
                                                    border: isSelected ? '1px solid rgba(122, 162, 247, 0.4)' : '1px solid transparent',
                                                    cursor: 'pointer',
                                                    transition: 'background 0.2s',
                                                    padding: '6px 6px'
                                                }}
                                            >
                                                <pre style={{
                                                    margin: 0,
                                                    color: isSelected ? '#7aa2f7' : '#a9b1d6',
                                                    fontSize: '0.72rem',
                                                    fontFamily: 'monospace',
                                                    lineHeight: 1.7,
                                                    whiteSpace: 'pre-wrap',
                                                    wordBreak: 'break-word'
                                                }}>
                                                    {(idx > 0 ? '' : '') + JSON.stringify(step, null, 2) + (idx < workflowSteps.length - 1 ? ',' : '')}
                                                </pre>
                                            </div>
                                        );
                                    })}

                                    {/* Closing brackets */}
                                    <pre style={{ margin: 0, padding: '4px 14px 0', color: '#a9b1d6', fontSize: '0.72rem', fontFamily: 'monospace', lineHeight: 1.7 }}>
                                        {`    ]\n  }\n}`}
                                    </pre>
                                </div>
                            </div>
                        );
                    })()
                }

            </Modal.Body >
        </Modal >
    );
};

export default WorkflowGraphModal;
