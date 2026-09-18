import { createContext, useContext, useState } from 'react';
import * as authApi from '../api/auth';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('user') || 'null');
    } catch {
      return null;
    }
  });

  function persist({ token, user: nextUser }) {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(nextUser));
    setUser(nextUser);
  }

  async function login(email, password) {
    const response = await authApi.login({ email, password });
    persist(response.data);
    return response.data.user;
  }

  async function register(name, email, password) {
    const response = await authApi.register({ name, email, password });
    persist(response.data); // registration auto-logs-in
    return response.data.user;
  }

  function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  }

  const value = {
    user,
    isAdmin: user?.role === 'ADMIN',
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
