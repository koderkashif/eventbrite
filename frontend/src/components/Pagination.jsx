export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;

  const pages = Array.from({ length: totalPages }, (_, i) => i);

  return (
    <div className="pagination">
      <button disabled={page === 0} onClick={() => onChange(page - 1)}>
        &laquo; Prev
      </button>
      {pages.map((p) => (
        <button key={p} className={p === page ? 'active' : ''} onClick={() => onChange(p)}>
          {p + 1}
        </button>
      ))}
      <button disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Next &raquo;
      </button>
    </div>
  );
}
