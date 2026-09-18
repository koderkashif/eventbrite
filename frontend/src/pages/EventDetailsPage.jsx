import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getErrorMessage } from '../api/client';
import { createBooking } from '../api/bookings';
import { getEvent } from '../api/events';
import { useAuth } from '../context/AuthContext';
import { formatDateTime } from '../components/EventCard';

export default function EventDetailsPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [bookingError, setBookingError] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getEvent(id)
      .then((res) => !cancelled && setEvent(res.data))
      .catch((err) => !cancelled && setError(getErrorMessage(err)))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [id]);

  async function handleBook() {
    setBusy(true);
    setBookingError('');
    try {
      const res = await createBooking({ eventId: event.id, quantity: Number(quantity) });
      navigate(`/confirmation/${res.data.id}`);
    } catch (err) {
      setBookingError(getErrorMessage(err)); // e.g. INSUFFICIENT_SEATS from the backend
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <p className="muted center">Loading event…</p>;
  if (error) return <div className="alert alert-error">{error}</div>;
  if (!event) return null;

  const maxQuantity = Math.min(10, event.availableSeats);

  return (
    <div className="details">
      <div className="details-main">
        <span className={`badge badge-${event.category?.toLowerCase()}`}>{event.category}</span>
        <span className={`badge badge-status-${event.status?.toLowerCase()}`}>{event.status}</span>
        <h2>{event.name}</h2>
        <p className="muted">{event.description}</p>

        <div className="detail-grid">
          <div>
            <strong>Starts</strong>
            <p>{formatDateTime(event.startTime)}</p>
          </div>
          <div>
            <strong>Ends</strong>
            <p>{formatDateTime(event.endTime)}</p>
          </div>
          <div>
            <strong>Location</strong>
            <p>
              {event.venue}, {event.city}
            </p>
          </div>
          <div>
            <strong>Capacity</strong>
            <p>
              {event.capacity} ({event.availableSeats} available)
            </p>
          </div>
        </div>
      </div>

      <aside className="details-side">
        <div className="price-tag">
          {Number(event.ticketPrice) === 0
            ? 'Free'
            : `₹${Number(event.ticketPrice).toFixed(2)}`}
          <small>per ticket</small>
        </div>

        {event.status === 'PUBLISHED' && event.availableSeats > 0 ? (
          user ? (
            <>
              <label>
                Tickets
                <select
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  disabled={busy}
                >
                  {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
              <button
                className="btn btn-primary btn-block"
                onClick={handleBook}
                disabled={busy || maxQuantity === 0}
              >
                {busy ? 'Booking…' : `Book ${quantity} ticket${quantity > 1 ? 's' : ''}`}
              </button>
              {bookingError && <div className="alert alert-error">{bookingError}</div>}
            </>
          ) : (
            <Link className="btn btn-primary btn-block" to="/login">
              Login to book
            </Link>
          )
        ) : (
          <p className="muted center">{event.availableSeats === 0 ? 'Sold out' : 'Not open for booking'}</p>
        )}
      </aside>
    </div>
  );
}
