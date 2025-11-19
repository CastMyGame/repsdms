# Frontend Implementation for Google Login

## Backend URL
The URL to initiate Google OAuth login is:
```
/oauth2/authorization/google
```

**Full URL examples:**
- Local development: `http://localhost:8080/oauth2/authorization/google`
- Production: `https://YOUR-BACKEND-DOMAIN/oauth2/authorization/google`

---

## React/Next.js Implementation

### Option 1: Simple Button with Redirect

```jsx
// LoginPage.jsx or LoginPage.tsx
import React from 'react';

const LoginPage = () => {
  const handleGoogleLogin = () => {
    // Replace with your actual backend URL
    const backendUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    window.location.href = `${backendUrl}/oauth2/authorization/google`;
  };

  return (
    <div className="login-container">
      <h2>Login</h2>
      
      {/* Regular login form */}
      <form onSubmit={handleRegularLogin}>
        {/* Your existing login form */}
      </form>

      {/* Google Login Button */}
      <button 
        onClick={handleGoogleLogin}
        className="google-login-btn"
        type="button"
      >
        <img src="https://www.google.com/favicon.ico" alt="Google" />
        Sign in with Google
      </button>
    </div>
  );
};

export default LoginPage;
```

### Option 2: Handle Callback on Home Page

Since your redirect URL is set to `https://www.repsdiscipline.com/home`, you need to handle the callback there:

```jsx
// HomePage.jsx or HomePage.tsx
import React, { useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

const HomePage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    // Check if this is a callback from Google OAuth
    const token = searchParams.get('token');
    const userParam = searchParams.get('user');

    if (token && userParam) {
      try {
        // Parse user data
        const user = JSON.parse(decodeURIComponent(userParam));
        
        // Store token in localStorage or your state management
        localStorage.setItem('authToken', token);
        localStorage.setItem('user', JSON.stringify(user));
        
        // Clear URL parameters
        navigate('/home', { replace: true });
        
        // Redirect to dashboard or wherever you want
        // navigate('/dashboard');
      } catch (error) {
        console.error('Error parsing OAuth callback:', error);
      }
    }
  }, [searchParams, navigate]);

  return (
    <div>
      {/* Your home page content */}
    </div>
  );
};
```

### Option 3: Complete Login Component with Styling

```jsx
// GoogleLoginButton.jsx
import React from 'react';
import './GoogleLoginButton.css';

const GoogleLoginButton = () => {
  const backendUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080';
  
  const handleGoogleLogin = () => {
    window.location.href = `${backendUrl}/oauth2/authorization/google`;
  };

  return (
    <button
      onClick={handleGoogleLogin}
      className="google-login-button"
      type="button"
    >
      <svg className="google-icon" viewBox="0 0 24 24">
        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
      </svg>
      <span>Sign in with Google</span>
    </button>
  );
};

export default GoogleLoginButton;
```

```css
/* GoogleLoginButton.css */
.google-login-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  max-width: 300px;
  padding: 12px 24px;
  background: white;
  border: 1px solid #dadce0;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  color: #3c4043;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.google-login-button:hover {
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.google-icon {
  width: 20px;
  height: 20px;
}
```

---

## Vanilla JavaScript/HTML

```html
<!-- login.html -->
<!DOCTYPE html>
<html>
<head>
  <title>Login</title>
</head>
<body>
  <div class="login-container">
    <h2>Login</h2>
    
    <!-- Regular login form -->
    <form id="loginForm">
      <!-- Your existing form -->
    </form>

    <!-- Google Login Button -->
    <button onclick="handleGoogleLogin()" class="google-btn">
      Sign in with Google
    </button>
  </div>

  <script>
    function handleGoogleLogin() {
      const backendUrl = 'http://localhost:8080'; // Replace with your backend URL
      window.location.href = `${backendUrl}/oauth2/authorization/google`;
    }

    // Handle callback on home page
    window.addEventListener('DOMContentLoaded', function() {
      const urlParams = new URLSearchParams(window.location.search);
      const token = urlParams.get('token');
      const userParam = urlParams.get('user');

      if (token && userParam) {
        try {
          const user = JSON.parse(decodeURIComponent(userParam));
          
          // Store token
          localStorage.setItem('authToken', token);
          localStorage.setItem('user', JSON.stringify(user));
          
          // Clear URL and redirect
          window.history.replaceState({}, document.title, '/home');
        } catch (error) {
          console.error('Error parsing OAuth callback:', error);
        }
      }
    });
  </script>
</body>
</html>
```

---

## Environment Variables

Create a `.env` file in your frontend project:

```env
# .env
REACT_APP_API_URL=http://localhost:8080

# For production
# REACT_APP_API_URL=https://your-backend-domain.com
```

---

## Important Notes

1. **Backend URL**: Make sure to use the correct backend URL (not frontend URL) for the OAuth initiation
2. **CORS**: Your backend already has CORS configured for your frontend domains
3. **Callback Handling**: Since you have `auth.sso.google.redirect-url` set, the backend will redirect to your frontend with `token` and `user` query parameters
4. **Token Storage**: Store the JWT token securely (localStorage, sessionStorage, or httpOnly cookies)
5. **Error Handling**: Handle cases where the user is not authorized (they'll be redirected back with an error)

---

## Testing

1. Make sure `auth.sso.google.enabled=true` in your backend
2. Ensure the user exists in your database with their Google email
3. Ensure the user has `enabled=true` in the database
4. Click the "Login with Google" button
5. After Google authentication, you'll be redirected to `/home?token=...&user=...`
6. Extract and store the token from the URL

