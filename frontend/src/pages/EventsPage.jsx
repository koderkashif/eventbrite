import { useEffect, useState } from 'react';
import EventCard from '../components/EventCard';
import Pagination from '../components/Pagination';
import { getErrorMessage } from '../api/client';
import { CATEGORIES, listEvents } from '../api/events';

const PAGE_SIZE = 9;

export default function EventsPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [page, setPage] = useState(0);
  const [searchInput, setSearchInput] = useState('');
  const [filters, setFilters] = useState({ search: '', category: '', city: '' });

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    listEvents({ page, size: PAGE_SIZE, sort: 'startTime,asc', ...filters })
      .then((res) => {
        if (!cancelled) {
          setData(res.data);
          setError('');
        }
      })
      .catch((err) => !cancelled && setError(getErrorMessage(err)))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [page, filters]);

  function applyFilters(e) {
    e.preventDefault();
    setPage(0);
    setFilters({ search: searchInput, category: e.target.category.value, city: e.target.city.value.trim() });
  }

  return (
    <>
      <div className="page-head">
        <h2>Discover events</h2>
      </div>

      <form className="filter-bar" onSubmit={applyFilters}>
        <input
          name="search"
          placeholder="Search events…"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
        />
        <select name="category" defaultValue={filters.category}>
          <option value="">All categories</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <input name="city" placeholder="City" defaultValue={filters.city} />
        <button className="btn btn-primary">Search</button>
      </form>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <p className="muted center">Loading events…</p>}

      {data && (
        <>
          <p className="muted small">{data.totalElements} event(s) found</p>
          {data.content.length === 0 ? (
            <p className="muted center">No events match your filters.</p>
          ) : (
            <div className="card-grid">
              {data.content.map((event) => (
                <EventCard key={event.id} event={event} />
              ))}
            </div>
          )}
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </>
  );
}
