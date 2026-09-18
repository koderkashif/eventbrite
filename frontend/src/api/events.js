import api, { cleanParams } from './client';

export const listEvents = (params) => api.get('/events', { params: cleanParams(params) });
export const getEvent = (id) => api.get(`/events/${id}`);
export const createEvent = (data) => api.post('/events', data);
export const updateEvent = (id, data) => api.put(`/events/${id}`, data);
export const deleteEvent = (id) => api.delete(`/events/${id}`);
export const adminListEvents = (params) => api.get('/events/admin', { params: cleanParams(params) });

export const CATEGORIES = ['TECH', 'MUSIC', 'SPORTS', 'BUSINESS', 'FOOD', 'ART'];
export const EVENT_STATUSES = ['DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'];
