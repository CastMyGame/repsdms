// Frontend code to parse Google OAuth callback
import React, { useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

const LoginPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const token = searchParams.get('token');
    const userParam = searchParams.get('user');

    if (token && userParam) {
      try {
        // Decode the URL-encoded JSON string
        const decodedUser = decodeURIComponent(userParam);
        
        // Parse the JSON string to get the user object
        const user = JSON.parse(decodedUser);
        
        // Extract values from the user object
        const userName = user.username || '';
        const schoolName = user.school || '';
        const email = user.username || ''; // username is the email
        const role = user.roles && user.roles.length > 0 
          ? user.roles[0].role 
          : '';

        // Store in sessionStorage exactly like traditional login
        sessionStorage.setItem("Authorization", token);
        sessionStorage.setItem("userName", userName);
        sessionStorage.setItem("schoolName", schoolName);
        sessionStorage.setItem("email", email);
        sessionStorage.setItem("role", role);

        // Clear URL parameters
        navigate('/login', { replace: true });
        
        // Optional: Redirect to dashboard
        // navigate('/dashboard');
        
      } catch (error) {
        console.error('Error parsing OAuth callback:', error);
        // Handle error - maybe show error message to user
      }
    }

    // Also check for OAuth error
    const error = searchParams.get('error');
    if (error) {
      const errorMessage = decodeURIComponent(error);
      console.error('OAuth Error:', errorMessage);
      // Display error to user
      navigate('/login', { replace: true });
    }
  }, [searchParams, navigate]);

  const handleGoogleLogin = () => {
    window.location.href = 'http://localhost:8080/oauth2/authorization/google';
  };

  return (
    <div className="login-container">
      <h2>Login</h2>
      
      {/* Your regular login form */}
      <form onSubmit={handleRegularLogin}>
        {/* Your existing form */}
      </form>

      {/* Google Login Button */}
      <button onClick={handleGoogleLogin} type="button">
        Sign in with Google
      </button>
    </div>
  );
};

export default LoginPage;

