import { useEffect, useState } from 'react';
import { getErrorMessage } from '../api/client';
import { cancelBooking, myBookings } from '../api/bookings';
import { formatDateTime } from '../components/EventCard';

export default function MyBookingsPage() {
  const [bookings, setBookings] = useState(null);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  async function load() {
    try {
      const res = await myBookings();
      setBookings(res.data);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleCancel(id) {
    if (!window.confirm('Cancel this booking? Seats will be released.')) return;
    setActionError('');
    try {
      await cancelBooking(id);
      await load();
    } catch (err) {
      setActionError(getErrorMessage(err));
    }
  }

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!bookings) return <p className="muted center">Loading your bookings…</p>;

  return (
    <>
      <div className="page-head">
        <h2>My bookings</h2>
      </div>

      {actionError && <div className="alert alert-error">{actionError}</div>}

      {bookings.length === 0 ? (
        <p className="muted center">
          No bookings yet — <a href="/">find an event</a>.
        </p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Reference</th>
              <th>Event</th>
              <th>Tickets</th>
              <th>Total</th>
              <th>Booked at</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {bookings.map((b) => (
              <tr key={b.id}>
                <td>
                  <code>{b.bookingReference}</code>
                </td>
                <td>{b.eventName}</td>
                <td>{b.quantity}</td>
                <td>₹{Number(b.totalAmount).toFixed(2)}</td>
                <td>{formatDateTime(b.createdAt)}</td>
                <td>
                  <span className={`badge badge-status-${b.status?.toLowerCase()}`}>{b.status}</span>
                </td>
                <td>
                  {b.status === 'CONFIRMED' && (
                    <button className="btn btn-danger btn-sm" onClick={() => handleCancel(b.id)}>
                      Cancel
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
