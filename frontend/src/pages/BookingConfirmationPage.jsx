import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getErrorMessage } from '../api/client';
import { getBooking } from '../api/bookings';

export default function BookingConfirmationPage() {
  const { id } = useParams();
  const [booking, setBooking] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getBooking(id)
      .then((res) => setBooking(res.data))
      .catch((err) => setError(getErrorMessage(err)));
  }, [id]);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!booking) return <p className="muted center">Loading booking…</p>;

  return (
    <div className="confirmation">
      <div className="confirmation-card">
        <div className="check">✓</div>
        <h2>You're going!</h2>
        <p className="muted">Booking reference</p>
        <p className="reference">{booking.bookingReference}</p>

        <div className="detail-grid left">
          <div>
            <strong>Event</strong>
            <p>{booking.eventName}</p>
          </div>
          <div>
            <strong>Tickets</strong>
            <p>{booking.quantity}</p>
          </div>
          <div>
            <strong>Price / ticket</strong>
            <p>₹{Number(booking.pricePerTicket).toFixed(2)}</p>
          </div>
          <div>
            <strong>Total paid</strong>
            <p>
              <strong>₹{Number(booking.totalAmount).toFixed(2)}</strong>
            </p>
          </div>
          <div>
            <strong>Status</strong>
            <p>{booking.status}</p>
          </div>
        </div>

        <div className="row-gap">
          <Link className="btn btn-primary" to="/my-bookings">
            View My Bookings
          </Link>
          <Link className="btn btn-outline" to="/">
            Browse more events
          </Link>
        </div>
      </div>
    </div>
  );
}
