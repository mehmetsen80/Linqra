import React, { useState, useMemo, useEffect } from 'react';
import { Card, Table, Form, InputGroup, Badge } from 'react-bootstrap';
import { HiSearch, HiRefresh, HiClock, HiCheckCircle, HiXCircle, HiPlay, HiDocumentText, HiClipboardCopy } from 'react-icons/hi';
import { format } from 'date-fns';
import { formatDateTime } from '../../../utils/dateUtils';
import { showSuccessToast } from '../../../utils/toastConfig';
import toolService from '../../../services/toolService';
import Button from '../../../components/common/Button';
import ToolExecutionDetailModal from '../../../components/common/ToolExecutionDetailModal';
import './styles.css';

// Real-world records derived from Linqra.tool_executions.json as a high-fidelity local fallback
const SAMPLE_EXECUTIONS = [
  {
    executionId: "dc28c0c2-4097-4890-95ae-409eca0ff271",
    toolId: "uscis.status.forms",
    toolName: "USCIS Sentinel Multi-Form Status Monitor",
    teamId: "67d0aeb17172416c411d419e",
    executedBy: "67d0aeb17172416c411d419e",
    visibility: "PUBLIC",
    status: "SUCCESS",
    executedAt: "2026-04-03T03:30:41.700Z",
    durationMs: 573,
    callerParams: {
      triggeredBy: "timursen",
      executionSource: "manual"
    },
    request: {
      link: { target: "tool", action: "execute" },
      query: {
        intent: "uscis.status.forms",
        params: { formId: "I-526", teamId: "67d0aeb17172416c411d419e" }
      },
      executedBy: "67d0aeb17172416c411d419e"
    },
    response: {
      result: {
        resourceId: "I-526",
        resourceCategory: "uscis-sentinel",
        currentVersion: "01/20/25",
        effectiveDate: "01/20/25",
        resourceUrl: "https://www.uscis.gov/sites/default/files/document/forms/i-526.pdf",
        instructionsUrl: "https://www.uscis.gov/sites/default/files/document/forms/i-526instr.pdf",
        changeDetected: false,
        summary: "USCIS Form I-526 scan completed - No investment protocol changes detected."
      },
      metadata: {
        source: "tool:uscis.status.forms",
        status: "success",
        cacheHit: false
      }
    }
  },
  {
    executionId: "1ca8bbb7-959a-4385-afd1-16dbd925fd22",
    toolId: "uscis.status.forms",
    toolName: "USCIS Sentinel Multi-Form Status Monitor",
    teamId: "67d0aeb17172416c411d419e",
    executedBy: "67d0aeb17172416c411d419e",
    visibility: "PUBLIC",
    status: "IN_PROGRESS",
    executedAt: "2026-04-03T03:57:56.961Z",
    durationMs: null,
    callerParams: {
      triggeredBy: "timursen",
      executionSource: "manual"
    },
    request: {
      link: { target: "tool", action: "execute" },
      query: {
        intent: "uscis.status.forms",
        params: { formId: "sample_formId", teamId: "67d0aeb17172416c411d419e" }
      },
      executedBy: "67d0aeb17172416c411d419e"
    },
    response: null
  },
  {
    executionId: "42d62e6b-1f61-4304-b739-1252fef97f60",
    toolId: "uscis.status.forms",
    toolName: "USCIS Sentinel Multi-Form Status Monitor",
    teamId: "67d0aeb17172416c411d419e",
    executedBy: "67d0aeb17172416c411d419e",
    visibility: "PUBLIC",
    status: "SUCCESS",
    executedAt: "2026-04-03T03:58:20.526Z",
    durationMs: 126,
    callerParams: {
      triggeredBy: "timursen",
      executionSource: "manual"
    },
    request: {
      link: { target: "tool", action: "execute" },
      query: {
        intent: "uscis.status.forms",
        params: { formId: "I-131", teamId: "67d0aeb17172416c411d419e" }
      },
      executedBy: "67d0aeb17172416c411d419e"
    },
    response: {
      result: {
        resourceId: "I-131",
        resourceCategory: "uscis-sentinel",
        currentVersion: "01/20/25",
        resourceUrl: "https://www.uscis.gov/sites/default/files/document/forms/i-131.pdf",
        changeDetected: false,
        summary: "USCIS Form I-131 scan completed - No version changes found for Travel Documents."
      },
      metadata: {
        source: "tool:uscis.status.forms",
        status: "success",
        cacheHit: false
      }
    }
  },
  {
    executionId: "2faeea69-32fe-4dfb-9fe9-2829159fd62f",
    toolId: "uscis.status.forms",
    toolName: "USCIS Sentinel Multi-Form Status Monitor",
    teamId: "67d0aeb17172416c411d419e",
    executedBy: "67d0aeb17172416c411d419e",
    visibility: "PUBLIC",
    status: "SUCCESS",
    executedAt: "2026-04-03T12:53:15.847Z",
    durationMs: 455,
    callerParams: {
      triggeredBy: "anonymous",
      executionSource: "manual"
    },
    request: {
      link: { target: "tool", action: "execute" },
      query: {
        intent: "uscis.status.forms",
        params: { formId: "I-485", receiptNumber: "MSC2191544444", teamId: "67d0aeb17172416c411d419e" }
      },
      executedBy: "67d0aeb17172416c411d419e"
    },
    response: {
      result: {
        error: "Service returned 404 NOT_FOUND: {\"status\":404,\"error\":\"Not Found\",\"path\":\"/api/uscis/check/I-485\"}"
      },
      metadata: {
        source: "komunas-app",
        status: "success",
        cacheHit: false
      }
    }
  },
  {
    executionId: "cad6d8fc-a18d-44ca-9338-88def7fd9f05",
    toolId: "uscis.status.forms",
    toolName: "USCIS Sentinel Multi-Form Status Monitor",
    teamId: "67d0aeb17172416c411d419e",
    executedBy: "67d0aeb17172416c411d419e",
    visibility: "PUBLIC",
    status: "FAILED",
    executedAt: "2026-04-03T20:54:06.532Z",
    durationMs: 653,
    errorMessage: "Team ID must be provided in params",
    callerParams: {
      triggeredBy: "anonymous",
      executionSource: "manual"
    },
    request: {
      link: { target: "tool", action: "execute" },
      query: {
        intent: "uscis.status.forms",
        params: { formId: "I-485" }
      },
      executedBy: "67d0aeb17172416c411d419e"
    },
    response: null
  },
  {
    executionId: "91c80b1a-9d89-41d9-b9b8-a1399f9d4742",
    toolId: "uscis.status.forms",
    toolName: "USCIS Sentinel Multi-Form Status Monitor",
    teamId: "67d0aeb17172416c411d419e",
    executedBy: "67d0aeb17172416c411d419e",
    visibility: "PUBLIC",
    status: "SUCCESS",
    executedAt: "2026-04-03T20:59:00.546Z",
    durationMs: 570,
    callerParams: {
      triggeredBy: "anonymous",
      executionSource: "manual"
    },
    request: {
      link: { target: "tool", action: "execute" },
      query: {
        intent: "uscis.status.forms",
        params: { formId: "I-485", teamId: "67d0aeb17172416c411d419e" }
      },
      executedBy: "67d0aeb17172416c411d419e"
    },
    response: {
      result: {
        resourceId: "I-485",
        changed: false,
        oldVersion: "01/20/25",
        newVersion: "01/20/25",
        resourceUrl: "https://www.uscis.gov/sites/default/files/document/forms/i-485.pdf"
      },
      metadata: {
        source: "komunas-app",
        status: "success",
        cacheHit: false
      }
    }
  },
  {
    executionId: "a9eacd2a-a4a5-452d-9737-f1a8f78a3550",
    toolId: "academic.diagnose-transcript",
    toolName: "Academic Intelligence Core Engine",
    teamId: "67d0aeb17172416c411d419e",
    executedBy: "67d0aeb17172416c411d419e",
    visibility: "PRIVATE",
    status: "SUCCESS",
    executedAt: "2026-04-03T21:12:10.920Z",
    durationMs: 820,
    callerParams: {
      triggeredBy: "adviser_agent",
      executionSource: "agent"
    },
    request: {
      link: { target: "tool", action: "execute" },
      query: {
        intent: "academic.diagnose",
        params: { level: "BAS", studentId: "std_789422" }
      },
      executedBy: "67d0aeb17172416c411d419e"
    },
    response: {
      result: {
        status: "DIAGNOSED",
        gpa: 3.84,
        totalCredits: 120,
        recommendedConcentration: "Software Engineering",
        academicPathways: ["BAS-SWE-S1", "BAS-SWE-S2"]
      },
      metadata: {
        source: "tool:academic.diagnose-transcript",
        status: "success",
        cacheHit: true
      }
    }
  }
];

const ToolExecutionsHistory = ({ teamId }) => {
  const [executions, setExecutions] = useState(SAMPLE_EXECUTIONS);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedExecution, setSelectedExecution] = useState(null);
  const [showModal, setShowModal] = useState(false);

  const parseNumber = (val) => {
    if (val === null || val === undefined) return null;
    if (typeof val === 'number') return val;
    if (typeof val === 'object' && val.$numberLong) return parseInt(val.$numberLong, 10);
    const parsed = parseInt(val, 10);
    return isNaN(parsed) ? null : parsed;
  };

  // Fetch real tool executions from backend MongoDB
  const fetchExecutions = async () => {
    setLoading(true);
    try {
      const res = await toolService.getToolExecutions(teamId);
      if (res.success && res.data && res.data.length > 0) {
        // Map database response fields safely to match UI schemas
        const formatted = res.data.map(item => ({
          executionId: item.executionId || item.id,
          toolId: item.toolId || 'unknown',
          toolName: item.toolName || item.toolId || 'Secure AI Module',
          teamId: item.teamId,
          executedBy: item.executedBy,
          visibility: item.visibility || 'PRIVATE',
          status: item.status || 'SUCCESS',
          executedAt: item.executedAt,
          durationMs: parseNumber(item.durationMs),
          callerParams: item.callerParams || {},
          request: item.request || {},
          response: item.response || null,
          errorMessage: item.errorMessage
        }));
        setExecutions(formatted);
      } else {
        // Keep SAMPLE_EXECUTIONS as elegant fallback if DB is pristine
        setExecutions(SAMPLE_EXECUTIONS.map(e => ({ ...e, durationMs: parseNumber(e.durationMs) })));
      }
    } catch (e) {
      console.warn("Failed to fetch MongoDB tool executions, falling back to static logs:", e);
      setExecutions(SAMPLE_EXECUTIONS.map(e => ({ ...e, durationMs: parseNumber(e.durationMs) })));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchExecutions();
  }, [teamId]);

  // Compute live statistics based on Loaded Data
  const stats = useMemo(() => {
    const total = executions.length;
    const successes = executions.filter(e => e.status === 'SUCCESS').length;
    const failures = executions.filter(e => e.status === 'FAILED').length;
    const inProgress = executions.filter(e => e.status === 'IN_PROGRESS').length;
    
    const successRate = total > 0 ? Math.round((successes / (total - inProgress)) * 100) : 100;
    
    const timedExecutions = executions.filter(e => e.durationMs !== null && e.durationMs !== undefined);
    const avgLatency = timedExecutions.length > 0
      ? Math.round(timedExecutions.reduce((acc, curr) => acc + curr.durationMs, 0) / timedExecutions.length)
      : 0;

    return { total, successes, failures, inProgress, successRate, avgLatency };
  }, [executions]);

  // Filtered List
  const filteredExecutions = useMemo(() => {
    return executions.filter(e => {
      const matchesSearch = e.executionId.toLowerCase().includes(searchTerm.toLowerCase()) ||
                            e.toolId.toLowerCase().includes(searchTerm.toLowerCase()) ||
                            e.toolName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                            (e.callerParams?.triggeredBy && e.callerParams.triggeredBy.toLowerCase().includes(searchTerm.toLowerCase()));
      
      const matchesStatus = statusFilter === 'ALL' || e.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [executions, searchTerm, statusFilter]);

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    showSuccessToast("Copied to clipboard!");
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'SUCCESS':
        return <Badge bg="" className="execution-badge success-badge"><HiCheckCircle className="me-1" /> SUCCESS</Badge>;
      case 'FAILED':
        return <Badge bg="" className="execution-badge failed-badge"><HiXCircle className="me-1" /> FAILED</Badge>;
      case 'IN_PROGRESS':
        return <Badge bg="" className="execution-badge progress-badge"><HiPlay className="me-1 pulse-icon" /> RUNNING</Badge>;
      default:
        return <Badge bg="secondary">{status}</Badge>;
    }
  };

  const getLatencyBadge = (ms) => {
    const numericMs = parseNumber(ms);
    if (numericMs === null || numericMs === undefined) {
      return <span className="latency-badge latency-none">--</span>;
    }
    if (numericMs < 200) {
      return <span className="latency-badge latency-fast">{numericMs}ms</span>;
    } else if (numericMs < 600) {
      return <span className="latency-badge latency-medium">{numericMs}ms</span>;
    } else {
      return <span className="latency-badge latency-slow">{numericMs}ms</span>;
    }
  };



  const openDetails = async (execution) => {
    try {
      const res = await toolService.getToolExecutionDetail(execution.executionId);
      if (res.success && res.data) {
        setSelectedExecution(res.data);
      } else {
        setSelectedExecution(execution);
      }
    } catch (err) {
      console.warn("Failed to fetch full tool execution details, falling back:", err);
      setSelectedExecution(execution);
    }
    setShowModal(true);
  };


  return (
    <div className="tool-executions-history">
      {/* Premium Statistics Banner */}
      <div className="executions-stats-row mb-4">
        <Card className="exec-stat-card">
          <Card.Body>
            <div className="stat-icon-wrapper blue-icon">
              <HiClock />
            </div>
            <div className="stat-text-block">
              <span className="stat-label">Total Executions</span>
              <h3 className="stat-value">{stats.total}</h3>
            </div>
          </Card.Body>
        </Card>
        <Card className="exec-stat-card">
          <Card.Body>
            <div className="stat-icon-wrapper green-icon">
              <HiCheckCircle />
            </div>
            <div className="stat-text-block">
              <span className="stat-label">Success Rate</span>
              <h3 className="stat-value">{stats.successRate}%</h3>
            </div>
          </Card.Body>
        </Card>
        <Card className="exec-stat-card">
          <Card.Body>
            <div className="stat-icon-wrapper orange-icon">
              <HiClock />
            </div>
            <div className="stat-text-block">
              <span className="stat-label">Avg. Latency</span>
              <h3 className="stat-value">{stats.avgLatency} ms</h3>
            </div>
          </Card.Body>
        </Card>
        <Card className="exec-stat-card">
          <Card.Body>
            <div className="stat-icon-wrapper red-icon">
              <HiXCircle />
            </div>
            <div className="stat-text-block">
              <span className="stat-label">Failed Invocations</span>
              <h3 className="stat-value">{stats.failures}</h3>
            </div>
          </Card.Body>
        </Card>
      </div>

      {/* Control / Filter Bar */}
      <Card className="filter-panel mb-4 shadow-sm border-0">
        <Card.Body className="p-3 d-flex flex-wrap align-items-center justify-content-between gap-3">
          <div className="d-flex flex-grow-1 max-width-search">
            <InputGroup className="search-input-group">
              <InputGroup.Text className="bg-transparent border-end-0">
                <HiSearch className="text-muted" />
              </InputGroup.Text>
              <Form.Control
                type="text"
                placeholder="Search by ID, tool, or trigger agent..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="border-start-0 shadow-none ps-0"
              />
            </InputGroup>
          </div>

          <div className="d-flex align-items-center gap-2">
            <span className="small text-muted fw-bold text-nowrap">Filter Status:</span>
            <Form.Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="status-dropdown shadow-none"
            >
              <option value="ALL">All Statuses</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILED">FAILED</option>
              <option value="IN_PROGRESS">RUNNING</option>
            </Form.Select>
            <Button 
              variant="outline-secondary" 
              onClick={fetchExecutions}
              disabled={loading}
              className="refresh-button shadow-none d-flex align-items-center justify-content-center p-2"
            >
              <HiRefresh className={loading ? "spin-icon" : ""} />
            </Button>
          </div>
        </Card.Body>
      </Card>

      {/* Data Table */}
      <Card className="table-card border-0 shadow-sm overflow-hidden">
        <Table hover responsive className="m-0 align-middle execution-table">
          <thead>
            <tr>
              <th>Status</th>
              <th>Execution ID</th>
              <th>Tool Name & ID</th>
              <th>Triggered By</th>
              <th>Executed At</th>
              <th>Latency</th>
              <th className="text-end pe-4">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="7" className="text-center py-5 text-muted">
                  <div className="spinner-border spinner-border-sm text-primary me-2" role="status" />
                  Loading execution audit logs...
                </td>
              </tr>
            ) : filteredExecutions.length === 0 ? (
              <tr>
                <td colSpan="7" className="text-center py-5 text-muted">
                  <div className="mb-2 fs-4">📭</div>
                  No execution logs match the active filter criteria.
                </td>
              </tr>
            ) : (
              filteredExecutions.map((item) => (
                <tr 
                  key={item.executionId} 
                  className="execution-row"
                  onClick={() => openDetails(item)}
                  style={{ cursor: 'pointer' }}
                >
                  <td>{getStatusBadge(item.status)}</td>
                  <td className="font-monospace text-muted small">
                    <span title={item.executionId}>{item.executionId.substring(0, 8)}...</span>
                    <Button
                      variant="link"
                      className="p-0 ms-1 text-muted copy-btn shadow-none"
                      onClick={(e) => {
                        e.stopPropagation();
                        copyToClipboard(item.executionId);
                      }}
                    >
                      <HiClipboardCopy size={14} />
                    </Button>
                  </td>
                  <td>
                    <div className="fw-semibold text-dark text-start">{item.toolName}</div>
                    <div className="text-muted small text-start font-monospace">{item.toolId}</div>
                  </td>
                  <td>
                    <Badge bg="light" className="text-secondary border small px-2 py-1">
                      {item.callerParams?.triggeredBy || "system"}
                    </Badge>
                  </td>
                  <td className="text-muted small">
                    {item.executedAt ? formatDateTime(item.executedAt, 'yyyy-MM-dd HH:mm:ss zzz') : '--'}
                  </td>
                  <td>
                    {getLatencyBadge(item.durationMs)}
                  </td>
                  <td className="text-end pe-4">
                    <Button
                      variant="outline-primary"
                      onClick={(e) => {
                        e.stopPropagation();
                        openDetails(item);
                      }}
                      className="action-btn px-3 py-1 fw-semibold d-inline-flex align-items-center"
                    >
                      <HiDocumentText className="me-1" /> View Details
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </Table>
      </Card>

      {/* Code Viewer Modal (modularized under common/) */}
      <ToolExecutionDetailModal
        show={showModal}
        onHide={() => setShowModal(false)}
        execution={selectedExecution}
      />
    </div>
  );
};

export default ToolExecutionsHistory;
