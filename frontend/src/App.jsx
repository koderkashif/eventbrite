import { Navigate, Route, Routes } from 'react-router-dom';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import BookingConfirmationPage from './pages/BookingConfirmationPage';
import EventDetailsPage from './pages/EventDetailsPage';
import EventsPage from './pages/EventsPage';
import LoginPage from './pages/LoginPage';
import MyBookingsPage from './pages/MyBookingsPage';
import RegisterPage from './pages/RegisterPage';
import AdminEventsPage from './pages/admin/AdminEventsPage';
import EventFormPage from './pages/admin/EventFormPage';

export default function App() {
  return (
    <div className="app">
      <Navbar />
      <main className="container">
        <Routes>
          <Route path="/" element={<EventsPage />} />
          <Route path="/events/:id" element={<EventDetailsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/confirmation/:id"
            element={
              <ProtectedRoute>
                <BookingConfirmationPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/my-bookings"
            element={
              <ProtectedRoute>
                <MyBookingsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/events"
            element={
              <ProtectedRoute adminOnly>
                <AdminEventsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/events/new"
            element={
              <ProtectedRoute adminOnly>
                <EventFormPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/events/:id/edit"
            element={
              <ProtectedRoute adminOnly>
                <EventFormPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <footer className="footer">
        Eventbrite showcase — React + Spring Boot microservices
      </footer>
    </div>
  );
}
