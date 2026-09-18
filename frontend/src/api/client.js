import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

// Attach the JWT to every request if we're logged in
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Uniform error message extraction: API's message > axios message > generic. */
export function getErrorMessage(error) {
  const fields = error.response?.data?.fieldErrors;
  if (fields) {
    const first = Object.values(fields)[0];
    if (first) return first;
  }
  return error.response?.data?.message || error.message || 'Something went wrong';
}

/** Drop empty params so the backend's enum/nullable filters don't choke on ''. */
export function cleanParams(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  );
}

export default api;
