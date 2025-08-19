// src/Login.js
import React from "react";
import { signInWithPopup } from "firebase/auth";
import { auth, provider,database } from "./firebase";
import { ref,get } from "firebase/database";
import "./Login.css";
import 'bootstrap/dist/css/bootstrap.min.css'


const Login = ({ setUser }) => {
  const login = async () => {
    try {
      const result = await signInWithPopup(auth, provider);
      const token = await result.user.getIdToken();
      const uid = result.user.uid;
      const email = result.user.email
      const safeEmail = btoa(email).replace(/\+/g, '-').replace(/\//g, '_');

      const response = await fetch ("http://localhost:8080/user/login",{
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      if (!response.ok){
        console.error("Failed to create user", await response.text());
      }else{
        console.log("User logged in app");
      }
      
      const snapshot = await get(ref(database, "user/" + safeEmail));
      let role = "ROLE_USER";
      let client = "";

      if (snapshot.exists() && snapshot.val().role){
        role = snapshot.val().role || "ROLE_USER";
        client = snapshot.val().client || "";
      }
      if (role ==="ROLE_SUPER_ADMIN"){
        client = ""
      }

      setUser({ email: email, token, displayName: result.user.displayName,uid,role,client });
      console.log("User logged in:", email);
    } catch (error) {
      console.error("Login error", error);
    }
  };

  return (<div className="full-center">
    <h2 className="mb-4">Login: </h2>
    <button onClick={login} type="button" className="login-with-google-btn">
      Sign in with Google</button>
  </div>
  );
};

export default Login;
