import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/** Route guard: requires login; `adminOnly` additionally requires the ADMIN role. */
export default function ProtectedRoute({ adminOnly = false, children }) {
  const { user, isAdmin } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (adminOnly && !isAdmin) {
    return <Navigate to="/" replace />;
  }
  return children;
}
