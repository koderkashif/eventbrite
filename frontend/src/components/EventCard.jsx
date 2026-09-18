import { Link } from 'react-router-dom';

export function formatDateTime(iso) {
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function EventCard({ event }) {
  return (
    <Link to={`/events/${event.id}`} className="card event-card">
      <div className="card-top">
        <span className={`badge badge-${event.category?.toLowerCase()}`}>{event.category}</span>
        <span className="seats-left">
          {event.availableSeats > 0 ? `${event.availableSeats} left` : 'Sold out'}
        </span>
      </div>
      <h3>{event.name}</h3>
      <p className="muted">{formatDateTime(event.startTime)}</p>
      <p className="muted">
        📍 {event.venue}, {event.city}
      </p>
      <div className="card-bottom">
        <strong>{Number(event.ticketPrice) === 0 ? 'Free' : `₹${Number(event.ticketPrice).toFixed(2)}`}</strong>
        <span className="link-hint">View details →</span>
      </div>
    </Link>
  );
}
