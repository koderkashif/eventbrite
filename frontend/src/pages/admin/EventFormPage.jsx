import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getErrorMessage } from '../../api/client';
import { CATEGORIES, EVENT_STATUSES, createEvent, getEvent, updateEvent } from '../../api/events';

/** datetime-local input value ("2026-12-01T18:00") -> ISO string the backend expects */
function toIso(localValue) {
  return localValue ? new Date(localValue).toISOString() : '';
}

/** ISO string -> datetime-local input value */
function toLocalInput(iso) {
  return iso ? iso.slice(0, 16) : '';
}

const EMPTY = {
  name: '',
  description: '',
  category: 'TECH',
  venue: '',
  city: '',
  startTime: '',
  endTime: '',
  ticketPrice: '',
  capacity: '',
  status: 'DRAFT',
};

export default function EventFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!isEdit) return;
    getEvent(id)
      .then((res) => {
        const e = res.data;
        setForm({
          name: e.name,
          description: e.description || '',
          category: e.category,
          venue: e.venue,
          city: e.city,
          startTime: toLocalInput(e.startTime),
          endTime: toLocalInput(e.endTime),
          ticketPrice: e.ticketPrice,
          capacity: e.capacity,
          status: e.status,
        });
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [id, isEdit]);

  function set(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    const payload = {
      name: form.name,
      description: form.description,
      category: form.category,
      venue: form.venue,
      city: form.city,
      startTime: toIso(form.startTime),
      endTime: toIso(form.endTime),
      ticketPrice: Number(form.ticketPrice),
      capacity: Number(form.capacity),
    };
    try {
      if (isEdit) {
        await updateEvent(id, { ...payload, status: form.status });
      } else {
        await createEvent(payload); // created as DRAFT; publish via edit
      }
      navigate('/admin/events');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="form-page">
      <div className="page-head">
        <h2>{isEdit ? 'Edit event' : 'Create event'}</h2>
        <Link to="/admin/events" className="btn btn-outline">
          ← Back
        </Link>
      </div>

      <form className="form-card wide" onSubmit={handleSubmit}>
        {error && <div className="alert alert-error">{error}</div>}

        <label>
          Name
          <input required maxLength={200} value={form.name} onChange={(e) => set('name', e.target.value)} />
        </label>

        <label>
          Description
          <textarea rows={4} value={form.description} onChange={(e) => set('description', e.target.value)} />
        </label>

        <div className="form-row">
          <label>
            Category
            <select value={form.category} onChange={(e) => set('category', e.target.value)}>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
          {isEdit && (
            <label>
              Status
              <select value={form.status} onChange={(e) => set('status', e.target.value)}>
                {EVENT_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>

        <div className="form-row">
          <label>
            Venue
            <input required value={form.venue} onChange={(e) => set('venue', e.target.value)} />
          </label>
          <label>
            City
            <input required value={form.city} onChange={(e) => set('city', e.target.value)} />
          </label>
        </div>

        <div className="form-row">
          <label>
            Start time
            <input
              type="datetime-local"
              required
              value={form.startTime}
              onChange={(e) => set('startTime', e.target.value)}
            />
          </label>
          <label>
            End time
            <input
              type="datetime-local"
              required
              value={form.endTime}
              onChange={(e) => set('endTime', e.target.value)}
            />
          </label>
        </div>

        <div className="form-row">
          <label>
            Ticket price (₹)
            <input
              type="number"
              min="0"
              step="0.01"
              required
              value={form.ticketPrice}
              onChange={(e) => set('ticketPrice', e.target.value)}
            />
          </label>
          <label>
            Capacity
            <input
              type="number"
              min="1"
              required
              value={form.capacity}
              onChange={(e) => set('capacity', e.target.value)}
            />
          </label>
        </div>

        {!isEdit && (
          <p className="muted small">New events start as DRAFT — publish them from the edit form.</p>
        )}

        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Saving…' : isEdit ? 'Save changes' : 'Create event'}
        </button>
      </form>
    </div>
  );
}
