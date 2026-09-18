import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/');
  }

  return (
    <nav className="navbar">
      <Link to="/" className="brand">
      🎟️ Eventbrite
      </Link>
      <div className="nav-links">
        <Link to="/">Events</Link>
        {user && <Link to="/my-bookings">My Bookings</Link>}
        {isAdmin && <Link to="/admin/events">Admin</Link>}
      </div>
      <div className="nav-auth">
        {user ? (
          <>
            <span className="user-chip">
              {user.name} <small>({user.role})</small>
            </span>
            <button className="btn btn-outline" onClick={handleLogout}>
              Logout
            </button>
          </>
        ) : (
          <>
            <Link className="btn btn-outline" to="/login">
              Login
            </Link>
            <Link className="btn btn-primary" to="/register">
              Sign up
            </Link>
          </>
        )}
      </div>
    </nav>
  );
}
