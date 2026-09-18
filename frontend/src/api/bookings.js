import api from './client';

export const createBooking = (data) =>
  api.post('/bookings', data, { headers: { 'Idempotency-Key': crypto.randomUUID() } });

export const getBooking = (id) => api.get(`/bookings/${id}`);
export const myBookings = () => api.get('/bookings/me');
export const cancelBooking = (id) => api.delete(`/bookings/${id}`);
