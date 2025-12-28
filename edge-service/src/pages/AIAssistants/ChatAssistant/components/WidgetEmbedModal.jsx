import React, { useState } from 'react';
import { Modal, Button, Alert } from 'react-bootstrap';
import { HiCheck, HiClipboardCopy } from 'react-icons/hi';
import { showSuccessToast } from '../../../../utils/toastConfig';

const WidgetEmbedModal = ({ show, onHide, assistant }) => {
    const [copiedCode, setCopiedCode] = useState(false);

    // Helper to get embed codes
    const getWidgetEmbedCode = () => {
        if (!assistant?.accessControl?.publicApiKey) {
            return null;
        }

        const baseUrl = window.location.origin;
        const publicApiKey = assistant.accessControl.publicApiKey;

        return {
            script: `<script src="${baseUrl}/widget/${publicApiKey}/script.js" async></script>`,
            iframe: `<iframe 
  src="${baseUrl}/widget/${publicApiKey}"
  width="400"
  height="600"
  frameborder="0"
  style="position: fixed; bottom: 20px; right: 20px; z-index: 9999;">
</iframe>`
        };
    };

    const handleCopyCode = (code) => {
        navigator.clipboard.writeText(code);
        setCopiedCode(true);
        showSuccessToast('Code copied to clipboard');
        setTimeout(() => setCopiedCode(false), 2000);
    };

    const isPublic = assistant?.accessControl?.type === 'PUBLIC';
    const embedCodes = getWidgetEmbedCode();

    return (
        <Modal show={show} onHide={onHide} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title>Embed Widget: {assistant?.name}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                {!isPublic ? (
                    <Alert variant="warning">
                        This assistant must be <strong>Public</strong> to be embedded. Please update its access control settings.
                    </Alert>
                ) : (
                    <>
                        <p className="text-muted mb-4">
                            Copy and paste the code below into your website's HTML to embed this assistant.
                        </p>

                        <h6 className="fw-bold">Script Tag (Recommended)</h6>
                        <p className="small text-muted mb-2">Adds a floating chat widget to your site.</p>
                        <div className="position-relative mb-4">
                            <div className="bg-light p-3 rounded border font-monospace small" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                                {embedCodes?.script}
                            </div>
                            <Button
                                variant="link"
                                size="sm"
                                className="position-absolute top-0 end-0 m-1"
                                onClick={() => handleCopyCode(embedCodes?.script)}
                                title="Copy to clipboard"
                            >
                                {copiedCode ? <HiCheck className="text-success" /> : <HiClipboardCopy />}
                            </Button>
                        </div>

                        <h6 className="fw-bold">Iframe</h6>
                        <p className="small text-muted mb-2">Embeds the chat interface directly into a page element.</p>
                        <div className="position-relative">
                            <div className="bg-light p-3 rounded border font-monospace small" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                                {embedCodes?.iframe}
                            </div>
                            <Button
                                variant="link"
                                size="sm"
                                className="position-absolute top-0 end-0 m-1"
                                onClick={() => handleCopyCode(embedCodes?.iframe)}
                                title="Copy to clipboard"
                            >
                                {copiedCode ? <HiCheck className="text-success" /> : <HiClipboardCopy />}
                            </Button>
                        </div>
                    </>
                )}
            </Modal.Body>
            <Modal.Footer>
                <Button variant="secondary" onClick={onHide}>
                    Close
                </Button>
            </Modal.Footer>
        </Modal>
    );
};

export default WidgetEmbedModal;
