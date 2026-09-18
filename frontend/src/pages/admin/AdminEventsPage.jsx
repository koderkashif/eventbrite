import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getErrorMessage } from '../../api/client';
import { adminListEvents, deleteEvent, updateEvent } from '../../api/events';
import { EVENT_STATUSES } from '../../api/events';
import Pagination from '../../components/Pagination';
import { formatDateTime } from '../../components/EventCard';

export default function AdminEventsPage() {
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  useEffect(() => {
    adminListEvents({ page, size: 10, sort: 'createdAt,desc', status: statusFilter })
      .then((res) => setData(res.data))
      .catch((err) => setError(getErrorMessage(err)));
  }, [page, statusFilter]);

  async function handleCancel(event) {
    setActionError('');
    try {
      await updateEvent(event.id, {
        name: event.name,
        description: event.description || '',
        category: event.category,
        venue: event.venue,
        city: event.city,
        startTime: event.startTime,
        endTime: event.endTime,
        ticketPrice: event.ticketPrice,
        capacity: event.capacity,
        status: 'CANCELLED',
      });
      const res = await adminListEvents({ page, size: 10, sort: 'createdAt,desc', status: statusFilter });
      setData(res.data);
    } catch (err) {
      setActionError(getErrorMessage(err));
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Permanently delete this event?')) return;
    setActionError('');
    try {
      await deleteEvent(id);
      const res = await adminListEvents({ page, size: 10, sort: 'createdAt,desc', status: statusFilter });
      setData(res.data);
    } catch (err) {
      setActionError(getErrorMessage(err));
    }
  }

  if (error) return <div className="alert alert-error">{error}</div>;

  return (
    <>
      <div className="page-head">
        <h2>Manage events</h2>
        <Link className="btn btn-primary" to="/admin/events/new">
          + New event
        </Link>
      </div>

      <div className="filter-bar">
        <select value={statusFilter} onChange={(e) => { setPage(0); setStatusFilter(e.target.value); }}>
          <option value="">All statuses</option>
          {EVENT_STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      {actionError && <div className="alert alert-error">{actionError}</div>}

      {data && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>When</th>
                <th>City</th>
                <th>Category</th>
                <th>Seats</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((event) => (
                <tr key={event.id}>
                  <td>
                    <Link to={`/events/${event.id}`}>{event.name}</Link>
                  </td>
                  <td>{formatDateTime(event.startTime)}</td>
                  <td>{event.city}</td>
                  <td>{event.category}</td>
                  <td>
                    {event.availableSeats}/{event.capacity}
                  </td>
                  <td>
                    <span className={`badge badge-status-${event.status?.toLowerCase()}`}>
                      {event.status}
                    </span>
                  </td>
                  <td className="actions">
                    <Link className="btn btn-outline btn-sm" to={`/admin/events/${event.id}/edit`}>
                      Edit
                    </Link>
                    {event.status === 'PUBLISHED' && (
                      <button className="btn btn-danger btn-sm" onClick={() => handleCancel(event)}>
                        Cancel
                      </button>
                    )}
                    {(event.status === 'DRAFT' || event.status === 'CANCELLED') && (
                      <button className="btn btn-danger btn-sm" onClick={() => handleDelete(event.id)}>
                        Delete
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </>
  );
}
