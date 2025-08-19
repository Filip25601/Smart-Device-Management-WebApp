import React, { useState, useEffect, useCallback } from "react";
import Login from "./Login";
import 'bootstrap/dist/css/bootstrap.min.css'
import { signOut } from "firebase/auth";
import { auth, database } from "./firebase";
import { onValue, ref,get } from "firebase/database";

function App() {

  //getters for door ac alarm
  const [doorsList, setDoorsList] = useState([]);
  const [airConditionsList, setAirConditionsList] = useState([]);
  const [alarmsList, setAlarmsList] = useState([]);
  //user
  const [user, setUser] = useState(null);
  const [users,setUsers] = useState([]);
  //doors
  const [newDoorName, setNewDoorName] = useState("");
  const [grantEmails, setGrantEmails] = useState({});
  //alarms
  const [newAlarmName, setNewAlarmName] = useState("");
  const [grantAlarmEmails, setGrantAlarmEmails] = useState({});
  //Airconditions
  const [newACName, setNewACName] = useState("");
  const [acGrantEmails, setAcGrantEmails] = useState({});
  //superadmin make admin and give client
  const [adminEmail, setAdminEmail] = useState("");
  const [clientName, setClientName] = useState("");
  //admin give client
  const [orgUserEmail, setOrgUserEmail] = useState("");
  //get access
  const [doorAccess, setDoorAccess] = useState({});
  const [alarmAccess, setAlarmAccess] = useState({});
  const [acAccess, setAcAccess] = useState({});

  //all log related
  const [logOffsets, setLogOffsets] = useState({});
  const [logs, setLogs] = useState({});

  const fetchLogs = (deviceId, type, offset = 0, limit = 3) => {
  const logRef = ref(database, `${type}/${deviceId}/logs`);
  get(logRef)
    .then((snapshot) => {
      const allLogs = Object.values(snapshot.val() || {}).sort(
        (a, b) => b.timestamp - a.timestamp
      );
      const visibleLogs = allLogs.slice(offset, offset + limit);

      setLogs((prev) => {
        const existing = prev[deviceId] || [];
        const newLogs = visibleLogs.filter(
          (log) => !existing.some((e)=> e.timestamp ===log.timestamp && e.message ===log.message)
        );
        return { ...prev, [deviceId]: [...existing, ...newLogs],
      };
  });
    })
    .catch((error) => {
      console.error("Failed to fetch logs", error);
    });
  };
  
  useEffect(() => {
    doorsList.forEach((door) => {
      if (!logs[door.id]) {
        fetchLogs(door.id, "door", 0);
      }
    });// eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doorsList]);

    useEffect(() => {
    alarmsList.forEach((alarm) => {
      if (!logs[alarm.id]) {
        fetchLogs(alarm.id, "alarm", 0);
      }
    });// eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alarmsList]);
    useEffect(() => {
    airConditionsList.forEach((airCondition) => {
      if (!logs[airCondition.id]) {
        fetchLogs(airCondition.id, "airConditions", 0);
      }
    });// eslint-disable-next-line react-hooks/exhaustive-deps
  }, [airConditionsList]);

//refresh logs
  const refreshLogs = (deviceId, type, limit = 3) => {
  const logRef = ref(database, `${type}/${deviceId}/logs`);
  get(logRef)
    .then((snapshot) => {
      const allLogs = Object.values(snapshot.val() || {}).sort(
        (a, b) => b.timestamp - a.timestamp
      );
      const latestLogs = allLogs.slice(0, limit);

      setLogs((prev) => ({
        ...prev,
        [deviceId]: latestLogs
      }));

      setLogOffsets((prev) => ({
        ...prev,
        [deviceId]: limit
      }));
    })
    .catch((error) => {
      console.error("Failed to refresh logs", error);
    });
};
//log

//temp slider
  const [localTemps, setLocalTemps] = useState({});
  
  useEffect(() => {
    setLocalTemps((prev) => {
      const updated = { ...prev };
      airConditionsList.forEach((ac) => {
        if (updated[ac.id] === undefined) {
          updated[ac.id] = ac.desiredTemperature;
        }
      });
      return updated;
    });
  }, [airConditionsList]);

//

  const getUserRoleByEmail = (email) => {
  const foundUser = users.find(u => u.email === email);
  return foundUser?.role || null;};

  
  const Roles ={
    ADMIN : "ROLE_ADMIN",
    USER : "ROLE_USER",
    SUPER_ADMIN : "ROLE_SUPER_ADMIN"
  };

  const decodeEmail = (encoded) => {
    try {
      return atob(encoded.replace(/-/g, "+").replace(/_/g, "/"));
    } catch (e) {
      return "Invalid email";
    }
  };
  
  const encodeEmail = (email) => {
  return btoa(email).replace(/\+/g, "-").replace(/\//g, "_");
};

  const userHasAccessToDoor = (doorId) => {
    if (user.role === Roles.ADMIN) {
    return true;
  }
    const encodedEmail = encodeEmail(user.email);
    return !!doorAccess[doorId]?.[encodedEmail];
  };

    const userHasAccessToAlarm = (alarmId) => {
      if (user.role === Roles.ADMIN) {
        return true;
      }
    const encodedEmail = encodeEmail(user.email);
    return !!alarmAccess[alarmId]?.[encodedEmail];
  };
    const userHasAccessToAc = (acId) => {
      if (user.role === Roles.ADMIN) {
        return true;
      }
    const encodedEmail = encodeEmail(user.email);
    return !!acAccess[acId]?.[encodedEmail];
  };


  //rerender 
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const interval = setInterval(() => {
      setTick(t => t + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, []);
  
  useEffect(() => {
  }, [tick]);
  //


  const logout = async () => 
    {await signOut(auth);
      setUser(null);
    };

  
//get users
  const fetchUsers = useCallback( async () => {
    if(!user) return;
    try{
      const res = await fetch("http://localhost:8080/user/all",{
        headers: {
          Authorization: "Bearer " + user.token,
        },
      });
      if (res.ok){
        const data = await res.json();
        setUsers(data);
      }else{
        console.error("Failed to fetch users");
      }
    }catch (err){
      console.error("Error fetching users: ",err);
    }
  },[user]);

  useEffect(() => {
    if (user && (user.role === Roles.SUPER_ADMIN || user.role === Roles.ADMIN)){
      fetchUsers();
    }
  }, [user, fetchUsers, Roles.SUPER_ADMIN,Roles.ADMIN]);

const deleteUser = async(email) => {
    const confirm = window.confirm(`Are you sure you want to delete user with this email - ${email}?`);
    if(!confirm) return;
    try{
    const res = await fetch(`http://localhost:8080/user/delete?email=${encodeURIComponent(email)}`, {  
      method: "DELETE",
      headers: {
          Authorization: "Bearer " + user.token,
        },
      });
      if (res.ok){
        alert("User deleted");
        fetchUsers();
      }else{
        console.error("Failed to delete user");
      }
    }catch (err){
      console.error("Error deleting user:",err);
    }
  };

//door subscribe
useEffect(() => {
  if(!user || !user.client)return;

  const doorsRef = ref(database,'door');
  const unsubscribe = onValue(doorsRef, (snapshot)=>{
    const data = snapshot.val();
    if (!data){
      setDoorsList([]);
      return;
    }
    const filteredDoors = Object.values(data).filter(
      (door) => door.client === user.client
    );

    setDoorsList(filteredDoors);
  });
  return () => unsubscribe();
},[user]);

//aircondition subscribe
useEffect(() => {
  if (!user || !user.client) return;

  const acRef = ref(database, 'airCondition');
  const unsubscribe = onValue(acRef, (snapshot) => {
    const data = snapshot.val();
    if (!data) {
      setAirConditionsList([]);
      return;
    }

    const filtered = Object.values(data).filter(
      (ac) => ac.client === user.client
    );

    setAirConditionsList(filtered);
  });

  return () => unsubscribe();
}, [user]);

//alarm subscribe
useEffect(() => {
  if (!user || !user.client) return;

  const alarmRef = ref(database, 'alarm');
  const unsubscribe = onValue(alarmRef, (snapshot) => {
    const data = snapshot.val();
    if (!data) {
      setAlarmsList([]);
      return;
    }

    const filtered = Object.values(data).filter(
      (alarm) => alarm.client === user.client
    );

    setAlarmsList(filtered);
  });

  return () => unsubscribe();
}, [user]);

//door_users subscribe
useEffect(() => {
  if(!user) return;
  
  const accessRef = ref(database,"door_users");
  const unsubscribe = onValue(accessRef, (snapshot)=> {
    const data = snapshot.val() || {};
    setDoorAccess(data);
  });
  return () => unsubscribe();
},[user]);

//alarm_users subscribe
useEffect(() => {
  if(!user) return;
  
  const alarmAccessRef = ref(database,"alarm_users");
  const unsubscribe = onValue(alarmAccessRef, (snapshot)=> {
    const data = snapshot.val() || {};
    setAlarmAccess(data);
  });
  return () => unsubscribe();
},[user]);

//airCondition_users subscribe
useEffect(() => {
  if(!user) return;
  
  const acAccessRef = ref(database,"airCondition_users");
  const unsubscribe = onValue(acAccessRef, (snapshot)=> {
    const data = snapshot.val() || {};
    setAcAccess(data);
  });
  return () => unsubscribe();
},[user]);

//revoke doors
const revokeAccess = async (doorId, email) => {
  const confirmRevoke = window.confirm(`Are you sure you want to revoke access from ${email}?`);
  if (!confirmRevoke) return;
  try {
    await fetch(`http://localhost:8080/doors/${doorId}/revoke?targetEmail=${encodeURIComponent(email)}`, {
      method: "DELETE",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (err) {
    console.error("Failed to revoke access", err);
  }
};
//revoke alarms
const revokeAlarmAccess = async (alarmId, email) => {
  const confirmRevoke = window.confirm(`Are you sure you want to revoke access from ${email}?`);
  if (!confirmRevoke) return;
  try {
    await fetch(`http://localhost:8080/alarms/${alarmId}/revoke?targetEmail=${encodeURIComponent(email)}`, {
      method: "DELETE",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (err) {
    console.error("Failed to revoke access", err);
  }
};
//revoke ac
const revokeACAccess = async (acId, email) => {
  const confirmRevoke = window.confirm(`Are you sure you want to revoke access from ${email}?`);
  if (!confirmRevoke) return;
  try {
    await fetch(`http://localhost:8080/airConditions/${acId}/revoke?targetEmail=${encodeURIComponent(email)}`, {
      method: "DELETE",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (err) {
    console.error("Failed to revoke access", err);
  }
};

  //actions
  const doorAction = async (id, action) => {
    try {
      await fetch(`http://localhost:8080/doors/${id}/${action}`, {
        method: "POST",
        headers: {
          Authorization: "Bearer " + user.token,
        },
      });
    } catch (error) {
      console.error(`Error performing ${action} on door ${id}:`, error);
    }
  };
  
  const deleteDoor = async (id) => {
    const confirmDelete = window.confirm("Are you sure that you want to delete this door?");
    if (!confirmDelete) return;
  try {
    const res = await fetch(`http://localhost:8080/doors/${id}`, {
      method: "DELETE",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
    if (res.ok) {
    } else {
      console.error("Failed to delete door");
    }
  } catch (err) {
    console.error("Error deleting door:", err);
  }
};

const createDoor = async () => {
    if(!newDoorName.trim())return;
    try {
      const res = await fetch(`http://localhost:8080/doors?name=${encodeURIComponent(newDoorName)}`, {
        method: "POST",
        headers: {
        Authorization: "Bearer " + user.token,
        },
      });
      if (res.ok) {
        setNewDoorName("");
      } else {
        console.error("Failed to create door");
      }
    } catch (err) {
      console.error(err);
    }
  };

const grantAccess = async (id,email) => {
  if (!email.trim()) return;
  try{
    const res = await fetch(
            `http://localhost:8080/doors/${id}/grant?targetEmail=${encodeURIComponent(email)}`,
            {
              method: "POST",
              headers: {
              Authorization: "Bearer " + user.token,
        },
      });

      if (res.ok) {
        alert(`Access granted to ${email}`);
      } else{
        const message = await res.text();
        alert (`Failed to grant access: ${message}`);
      }
  } catch(error){
    console.error("Error granting access: ",error)
  }
};

const alarmAction = async (id, action) => {
  try {
    await fetch(`http://localhost:8080/alarms/${id}/${action}`, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (error) {
    console.error(`Error performing ${action} on alarm ${id}:`, error);
  }
};

const deleteAlarm = async (id) => {
  const confirmDelete = window.confirm("Are you sure you want to delete this alarm?");
  if (!confirmDelete) return;
  try {
    await fetch(`http://localhost:8080/alarms/${id}`, {
      method: "DELETE",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (err) {
    console.error("Error deleting alarm:", err);
  }
};

const createAlarm = async () => {
  if (!newAlarmName.trim()) return;
  try {
    const res = await fetch(`http://localhost:8080/alarms?name=${encodeURIComponent(newAlarmName)}`, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
    if (res.ok) {
      setNewAlarmName("");
    } else {
      console.error("Failed to create alarm");
    }
  } catch (err) {
    console.error(err);
  }
};

const grantAlarmAccess = async (id, email) => {
  if (!email.trim()) return;
  try {
    const res = await fetch(
      `http://localhost:8080/alarms/${id}/grant?targetEmail=${encodeURIComponent(email)}`,
      {
        method: "POST",
        headers: {
          Authorization: "Bearer " + user.token,
        },
      }
    );
    if (res.ok) {
      alert(`Access granted to ${email}`);
    } else {
      const msg = await res.text();
      alert(`Failed to grant alarm access: ${msg}`);
    }
  } catch (err) {
    console.error("Error granting alarm access:", err);
  }
};

const airConditionAction = async (id, action) => {
  try {
    await fetch(`http://localhost:8080/airConditions/${id}/${action}`, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (error) {
    console.error(`Error performing ${action} on AC ${id}:`, error);
  }
};

const setAirConditionTemperature = async (id, value) => {
  try {
    await fetch(`http://localhost:8080/airConditions/${id}/temperature?value=${encodeURIComponent(value)}`, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (error) {
    console.error(`Error setting temperature`, error);
  }
};


const setAirConditionMode = async (id, mode) => {
  try {
    await fetch(`http://localhost:8080/airConditions/${id}/mode?value=${encodeURIComponent(mode)}`, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
  } catch (error) {
    console.error(`Error changing mode`, error);
  }
};


const deleteAirCondition = async (id) => {
  const confirmDelete = window.confirm("Delete this air conditioner?");
  if (!confirmDelete) return;
  try {
    const res = await fetch(`http://localhost:8080/airConditions/${id}`, {
      method: "DELETE",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
    if (res.ok) {
    } else {
      console.error("Failed to delete AC");
    }
  } catch (err) {
    console.error("Error deleting AC:", err);
  }
};

const createAirCondition = async () => {
  if (!newACName.trim()) return;
  try {
    const res = await fetch(`http://localhost:8080/airConditions?name=${encodeURIComponent(newACName)}`, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });
    if (res.ok) {
      setNewACName("");
    } else {
      console.error("Failed to create AC");
    }
  } catch (err) {
    console.error(err);
  }
};

const grantACAccess = async (id, email) => {
  if (!email.trim()) return;
  try {
    const res = await fetch(`http://localhost:8080/airConditions/${id}/grant?targetEmail=${encodeURIComponent(email)}`, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + user.token,
      },
    });

    if (res.ok) {
      alert(`Access granted to ${email}`);
    } else {
      const message = await res.text();
      alert(`Failed to grant access: ${message}`);
    }
  } catch (error) {
    console.error("Error granting AC access: ", error);
  }
};

const assignUserToClient = async () => {
    try {
      const res = await fetch(
        `http://localhost:8080/user/assign-client?targetEmail=${encodeURIComponent(orgUserEmail)}&client=${encodeURIComponent(user.client)}`, {
          method: "POST",
          headers: {
          Authorization: "Bearer " + user.token,
          },
        });

      if (res.ok) {
      alert("User assigned to client successfully");
      setOrgUserEmail("");
      }else{
        const message = await res.text();
        alert(`Failed to grant access: ${message}`);
      }
    } catch (err) {
      console.error("Failed to assign user to client", err);
      alert("Error: " + err.message);
    }
  };

const makeAdmin = async () => {
  try{
    const res = await fetch(`http://localhost:8080/user/make-admin?targetEmail=${encodeURIComponent(adminEmail)}&client=${encodeURIComponent(clientName)}`, {
        method: "POST",
        headers: {
            Authorization: "Bearer " + user.token,
        },
      });
      
      if (res.ok){
        alert ("Admin promoted successfully");
        setAdminEmail("");
        setClientName("");
        fetchUsers();
      }else {
        alert ("Failed to promote admin");
      }
      }catch (err){
        console.error("Error promoting admin: ",err);
      }
};

const [activeBreakInAlarm, setActiveBreakInAlarm] = useState(null);
useEffect(() => {
  const triggered = alarmsList.find((alarm) => alarm.breakIn);
  setActiveBreakInAlarm(triggered || null);
}, [alarmsList]);


if (!user) return <Login setUser={setUser} />;
  return (
    <div className="container mt-4">

      <div className="d-flex justify-content-between align-items-center mb-4">
        <h1>Welcome, {user.displayName}</h1>
        <h1> {user.client}</h1>
        <button title="Logout" className="btn btn-outline-secondary" onClick={logout}><i class="bi bi-box-arrow-right fs-5"></i></button>
      </div>

      { user.role ===Roles.SUPER_ADMIN && (
        <form className="d-flex mb-3"
        onSubmit={(e) => {
          e.preventDefault();
          makeAdmin();
        }}>
          <input type="email" required className="form-control me-2" placeholder="Email of user"
          value={adminEmail}
          onChange={(e) => setAdminEmail(e.target.value)}
          />
          <input type="text" required className="form-control me-2" placeholder="Client name"
          value={clientName}
          onChange={(e) => setClientName(e.target.value.toLowerCase())}
          />
          <button
          title="Create admin/organisation"
          type="submit"
          className="btn btn-primary"
          ><i class="bi bi-plus-square fs-4"></i>
          </button>
        </form>
      )}
      
      {user.role === Roles.SUPER_ADMIN && (
        <div className="mt-4">
          <h3>All users</h3>
          <table className="table table-bordered">
            <thead>
              <tr>
                <th>Display Name</th>
                <th>Email</th>
                <th>Client</th>
                <th>Delete</th>
                </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.email}>
                      <td>{u.displayName}</td>
                      <td>{u.email}</td>
                      <td>{u.client}</td>
                      <td>
                        <button
                        className="btn btn-danger btn-sm"
                        onClick={() => deleteUser(u.email)}
                        title="Delete"
                        ><i class="bi bi-trash-fill fs-4"></i>
                        </button>
                        </td>
                        </tr>
                      ))}
                      </tbody>
                      </table>
                      </div>
                    )}
      
      {user.role === Roles.ADMIN && (
        <form
        className="mb-3 d-flex align-items-center"
        onSubmit={(e) => {
          e.preventDefault();
          assignUserToClient();
        }}
        >
          <input
          type="email"
          required
          className="form-control me-2"
          placeholder="Enter email to add user to organization"
          value={orgUserEmail}
          onChange={(e) => setOrgUserEmail(e.target.value)}
          />
          <button
          type="submit"
          className="btn btn-outline-success"
          title="Add user to organization"
          >
            <i className="bi bi-person-plus fs-5"></i> Add User
            </button>
            </form>
          )}
    
                      
      {user.role === Roles.ADMIN &&(
        <div className="mb-3 d-flex">
          <input
          type="text"
          className="form-control me-2"
          placeholder="Enter door name"
          value={newDoorName}
          onChange={(e) => setNewDoorName(e.target.value)}/>
          <button
          title="Create Door"
          onClick={createDoor}
          className = {`btn mb-0 ${newDoorName.trim() ? "btn-primary" : "btn-secondary"}`}
          disabled={!newDoorName.trim()}><i class="bi bi-file-earmark-plus fs-1"></i>
          </button>
        </div>
      )}

      {user.role === Roles.ADMIN && (
      <div className="mb-3 d-flex">
        <input
          type="text"
          className="form-control me-2"
          placeholder="Enter air conditioner name"
          value={newACName}
          onChange={(e) => setNewACName(e.target.value)}
        />
        <button
          title="Create Air Conditioner"
          onClick={createAirCondition}
          className={`btn mb-0 ${newACName.trim() ? "btn-primary" : "btn-secondary"}`}
          disabled={!newACName.trim()}
        >
          <i className="bi bi-plus-square fs-1"></i>
        </button>
      </div>
    )}

      {user.role === Roles.ADMIN && (
        <div className="mb-3 d-flex">
          <input
            type="text"
            className="form-control me-2"
            placeholder="Enter alarm name"
            value={newAlarmName}
            onChange={(e) => setNewAlarmName(e.target.value)}
          />
          <button
            title="Create Alarm"
            onClick={createAlarm}
            className={`btn mb-0 ${newAlarmName.trim() ? "btn-primary" : "btn-secondary"}`}
            disabled={!newAlarmName.trim()}
          >
            <i className="bi bi-plus-circle fs-1"></i>
          </button>
        </div>
      )}

  {(user.role === Roles.ADMIN || user.role === Roles.USER) && (
    <>
    {doorsList.length === 0 ? (
      <p></p>
    ) : (  
      <div className="list-unstyled">
        <h2>All doors</h2>
        {doorsList.map((door) => {
          const hasAccess = userHasAccessToDoor(door.id);
          return(
          <div key={door.id} className="border p-3 mb-3 rounded">
            <div className="row">
              <div className="col-md-6">
                <p><strong>Name: </strong> {door.name}</p>
                <p>
                  <strong>Status: </strong>
                  {(Date.now() - door.lastPing < 5000) ? (
                    <i className="bi bi-wifi text-success ms-1 fs-2" title="Online"></i>
                  ) : (
                    <i className="bi bi-wifi-off text-danger ms-1 fs-2" title="Offline"></i>
                  )}
                </p>
                <p>
                  <strong>Door: </strong>
                  <i 
                  className={`bi ${door.open ? "bi-door-open-fill text-success" : "bi-door-closed-fill text-secondary"} fs-2`}
                  title={door.open ? "Open" : "Closed"}></i>
                </p>
                <p>
                  <strong>Lock status: </strong>
                  <i className={`bi ${door.locked ? "bi-lock-fill text-danger" : "bi-unlock-fill text-success"} fs-2`} title={door.locked ? "Locked" : "Unlocked"}></i>
                </p>

            {hasAccess && (
              <>
                <button
                  onClick={() => doorAction(door.id, door.locked ? "unlock" : "lock")}
                  disabled={door.open && !door.locked}
                  className={`btn btn-sm me-2 ${door.locked ? "btn-danger" : "btn-info"}`}
                  title={door.locked ? "Unlock" : "Lock"}
                >
                  <i className={`bi ${door.locked ? "bi-lock-fill" : "bi-unlock-fill"} fs-3`}></i>
                </button>
                <button
                  onClick={() => deleteDoor(door.id)}
                  className="btn btn-sm btn-danger"
                  title="Delete door"
                >
                  <i className="bi bi-trash fs-3"></i>
                </button>
              </>
            )}
          {user.role === Roles.ADMIN && (
            <>
              <form
                className="mt-3 d-flex align-items-center"
                onSubmit={(e) => {
                  e.preventDefault();
                  grantAccess(door.id, grantEmails[door.id]);
                }}
              >
                <input
                  type="email"
                  required
                  className="form-control d-inline-block me-2"
                  placeholder="Enter user email to grant access"
                  value={grantEmails[door.id] || ""}
                  onChange={(e) =>
                    setGrantEmails((prev) => ({
                      ...prev,
                      [door.id]: e.target.value,
                    }))
                  }
                  style={{ width: "300px" }}
                />
                <button
                  type="submit"
                  className="btn btn-sm btn-outline-primary fs-5"
                >
                  Grant Access
                </button>
              </form>

          {doorAccess[door.id] && (
            <div className="mt-3">
              <p><strong>Authorized Users:</strong></p>
              <ul>
                {Object.keys(doorAccess[door.id]).map((encodedEmail) => {
                  const email = decodeEmail(encodedEmail);
                  const isCurrentUser = email === user.email;
                  const targetRole = getUserRoleByEmail(email);
                  const isTargetAdmin = targetRole === Roles.ADMIN || targetRole === Roles.SUPER_ADMIN;
                  return (
                    <li key={encodedEmail}>
                      {email}
                      {!isCurrentUser && !isTargetAdmin ? (
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-danger ms-2"
                          onClick={() => revokeAccess(door.id, email)}
                        >
                          Revoke
                        </button>
                      ) : (
                        <span className="text-muted ms-2">({isCurrentUser ? "You" : "Admin"})</span>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </>
      )}

        </div>
        <div className="col-md-6 border-md-start ps-md-4 mt-3 mt-md-0">
            <p><strong>Logs:</strong></p>
            {logs[door.id]?.map((log, index) => (
              <p key={index}><em>{new Date(log.timestamp).toLocaleString()}:</em> {log.message}</p>
            ))}
            <button
              className="btn btn-sm btn-outline-secondary me-2"
              onClick={() => {
                const nextOffset = (logOffsets[door.id] || 0) + 3;
                fetchLogs(door.id, "door", nextOffset);
                setLogOffsets((prev) => ({ ...prev, [door.id]: nextOffset }));
              }}
            >
              Show more logs
            </button>
            <button
              className="btn btn-sm btn-outline-primary"
              onClick={() => refreshLogs(door.id, "door")}
            >
              Refresh Logs
            </button>
          </div>
        </div>
        </div>
        );}
        )}
        </div>
      )}
      </>
    )}

  {(user.role === Roles.ADMIN || user.role === Roles.USER) && (
    <>
      {airConditionsList.length === 0 ? (
        <p></p>
      ) : (
        <div className="list-unstyled">
          <h2>All air conditioners</h2>
          {airConditionsList.map((ac) =>{
            const hasAccess = userHasAccessToAc(ac.id);
            return(
            <div key={ac.id} className="border p-3 mb-3 rounded">
              <div className="row">
              <div className="col-md-6">
              <p><strong>Name:</strong> {ac.name}</p>
              <p>
                <strong>Status:</strong>{" "}
                {(Date.now() - ac.lastPing < 5000) ? (
                  <i className="bi bi-wifi text-success ms-1 fs-2" title="Online"></i>
                ) : (
                  <i className="bi bi-wifi-off text-danger ms-1 fs-2" title="Offline"></i>
                )}
              </p>
              <p>
                <strong>Working:</strong>{" "}
                <i
                  className={`bi ${ac.isWorking ? "bi-toggle-on text-success" : "bi-toggle-off text-danger"} fs-3`}
                  title={ac.isWorking ? "On" : "Off"}
                ></i>
              </p>
              <p>
                <strong>Current Temperature: </strong>{ac.currentTemperature}°C{" "}
                  {ac.currentTemperature > 28 && (
                    <i className="bi bi-thermometer-sun text-danger ms-1" title="Hot"></i>
                  )}
                  {ac.currentTemperature < 18 && (
                    <i className="bi bi-thermometer-snow text-info fs-5" title="Cold"></i>
                  )}
              </p>
              <p>
                <strong>Mode: </strong>{" "}
                <i 
                className={`${ac.mode === "heat" ? "bi bi-sun-fill text-warning" : "bi bi-snow text-primary"} fs-3`}
                title={ac.mode === "heat"? "heat" : "cold"}>
                </i>
              </p>

              {hasAccess && (
                <>
                  <button
                    onClick={() =>
                      airConditionAction(ac.id, ac.isWorking ? "off" : "on")
                    }
                    className={`btn btn-sm me-2 ${
                      ac.isWorking ? "btn-success" : "btn-danger"
                    }`}
                    title={ac.isWorking ? "Turn Off" : "Turn On"}
                  >
                    <i
                      className={`bi ${ac.isWorking ? "bi-toggle-on" : "bi-toggle-off"} fs-3`}
                    ></i>
                  </button>
                  <button
                    onClick={() => deleteAirCondition(ac.id)}
                    className="btn btn-sm btn-danger"
                    title="Delete"
                  >
                    <i className="bi bi-trash fs-3"></i>
                  </button>
                  <div className="mt-2">
                    <strong>Change mode: </strong>
                    <div>
                      <label className="me-2">
                        <input
                          type="radio"
                          name={`mode-${ac.id}`}
                          value="cold"
                          checked={ac.mode === "cold"}
                          onChange={() => setAirConditionMode(ac.id, "cold")}
                        />{" "}
                        Cold
                      </label>
                      <label>
                        <input
                          type="radio"
                          name={`mode-${ac.id}`}
                          value="heat"
                          checked={ac.mode === "heat"}
                          onChange={() => setAirConditionMode(ac.id, "heat")}
                        />{" "}
                        Heat
                      </label>
                    </div>
                  </div>
                  <div className="mt-2">
                    <strong>Desired Temperature: </strong>
                    <div className="d-flex align-items-center">
                      <input
                        type="range"
                        min={
                            ac.mode === "cold"
                              ? 16
                              : ac.currentTemperature + 0.1
                          }
                          max={
                            ac.mode === "heat"
                              ? 30
                              : ac.currentTemperature - 0.1
                          }
                        step="0.1"
                        value={localTemps[ac.id] ?? ac.desiredTemperature}
                        onChange={(e) => {
                          const value = Math.round(e.target.value * 10) / 10;
                          setLocalTemps((prev) => ({ ...prev, [ac.id]: value }));
                        }}
                        onMouseUp={() => {
                          setAirConditionTemperature(ac.id, localTemps[ac.id]);
                        }}
                        onTouchEnd={() => {
                          setAirConditionTemperature(ac.id, localTemps[ac.id]);
                        }}
                        className="form-range"
                        style={{ width: "200px" }}
                      /><span className="ms-3">{(localTemps[ac.id] ?? ac.desiredTemperature).toFixed(1)}°C</span>
                    </div>
                  </div>
                </>
              )}

              {user.role === Roles.ADMIN && (
                <>
                <form
                  className="mt-3 d-flex align-items-center"
                  onSubmit={(e) => {
                    e.preventDefault();
                    grantACAccess(ac.id, acGrantEmails[ac.id]);
                  }}
                >
                  <input
                    type="email"
                    required
                    className="form-control d-inline-block me-2"
                    placeholder="Enter user email to grant access"
                    value={acGrantEmails[ac.id] || ""}
                    onChange={(e) =>
                      setAcGrantEmails((prev) => ({ ...prev, [ac.id]: e.target.value }))
                    }
                    style={{ width: "300px" }}
                  />
                  <button
                    type="submit"
                    className="btn btn-sm btn-outline-primary fs-5"
                  >
                    Grant Access
                  </button>
                </form>

                {acAccess[ac.id] && (
                  <div className="mt-3">
                    <p><strong>Authorized Users: </strong></p>
                    <ul>
                      {Object.keys(acAccess[ac.id]).map((encodedEmail) => {
                        const email = decodeEmail(encodedEmail);
                          const isCurrentUser = email === user.email;
                          const targetRole = getUserRoleByEmail(email);
                          const isTargetAdmin = targetRole === Roles.ADMIN || targetRole === Roles.SUPER_ADMIN;
                          return (
                          <li key={encodedEmail}>
                            {email}
                            {!isCurrentUser && !isTargetAdmin ? (
                              <button
                              type="button"
                              className="btn btn-sm btn-outline-danger ms-2"
                              onClick={() => revokeACAccess(ac.id, email)}
                            >
                              Revoke
                            </button>
                            ):(
                            <span className="text-muted ms-2">({isCurrentUser ? "You" : "Admin"})</span>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                )}
                </>
              )}
              </div>
              <div className="col-md-6 border-md-start ps-md-4 mt-3 mt-md-0">
                <p><strong>Logs:</strong></p>
                {logs[ac.id]?.map((log, index) => (
                  <p key={index}>
                    <em>{new Date(log.timestamp).toLocaleString()}:</em>
                    {log.message}
                  </p>
                ))}
                <button
                  className="btn btn-sm btn-outline-secondary me-2"
                  onClick={() => {
                    const nextOffset = (logOffsets[ac.id] || 0) + 3;
                    fetchLogs(ac.id, "airCondition", nextOffset);
                    setLogOffsets((prev) => ({ ...prev, [ac.id]: nextOffset }));
                  }}
                >
                  Show more logs
                </button>
                <button
                  className="btn btn-sm btn-outline-primary"
                  onClick={() => refreshLogs(ac.id, "airCondition")}
                >
                  Refresh Logs
                </button>
              </div>

            </div>
            </div>
            )}
          )}
        </div>
      )}
    </>
  )}
  
  {(user.role === Roles.ADMIN || user.role === Roles.USER) && (
  <>
    {alarmsList.length === 0 ? (
      <p></p>
    ) : (
      <div className="list-unstyled">
        <h2>All alarms</h2>
        {alarmsList.map((alarm) => {
          const hasAccess = userHasAccessToAlarm(alarm.id);
          return (
          <div key={alarm.id} className="border p-3 mb-3 rounded">
            <div className="row">
            <div className="col-md-6">
            {activeBreakInAlarm && (
              <div
                className="position-fixed top-0 start-0 w-100 h-100 bg-dark bg-opacity-75 d-flex flex-column justify-content-center align-items-center text-white"
                style={{ zIndex: 9999 }}
              >
                <h1 className="display-3 text-danger mb-4">
                  🚨🚨🚨 BREAK-IN ALERT! 🚨🚨🚨
                </h1>
                <p className="fs-4 mb-3">Alarm: <strong>{activeBreakInAlarm.name}</strong></p>
                <button
                    className="btn btn-lg btn-light"
                    onClick={() => alarmAction(activeBreakInAlarm.id, "endBreakIn")}
                  >
                    End Alarm
                  </button>
              </div>
            )}
            <p><strong>Name: </strong> {alarm.name}</p>
            <p>
              <strong>Status: </strong>
                {(Date.now() - alarm.lastPing < 5000) ? (
                  <i className="bi bi-wifi text-success ms-1 fs-2" title="Online"></i>
                ) : (
                  <i className="bi bi-wifi-off text-danger ms-1 fs-2" title="Offline"></i>
                )}
              </p>
              <p>
                <strong>Alarm: </strong>
                <i
                  className={`bi ${alarm.isWorking ? "bi-bell-fill text-danger" : "bi-bell-slash-fill text-secondary"} fs-2`}
                  title={alarm.isWorking ? "On" : "Off"}
                ></i>
              </p>

              
              {hasAccess && (
                <>
                  <button
                    onClick={() => alarmAction(alarm.id, alarm.isWorking ? "off" : "on")}
                    className={`btn btn-sm me-2 ${alarm.isWorking ? "btn-success" : "btn-warning"}`}
                    title={alarm.isWorking ? "Turn Off" : "Turn On"}
                  >
                    <i className={`bi ${alarm.isWorking ? "bi-alarm" : "bi-alarm-fill"} fs-3`}></i>
                  </button>
                  <button
                    onClick={() => deleteAlarm(alarm.id)}
                    className="btn btn-sm btn-danger"
                    title="Delete alarm"
                  >
                    <i className="bi bi-trash fs-3"></i>
                  </button>
                </>
              )}

            {user.role === Roles.ADMIN && (
              <>
              <form
                className="mt-3 d-flex align-items-center"
                onSubmit={(e) => {
                  e.preventDefault();
                  grantAlarmAccess(alarm.id, grantAlarmEmails[alarm.id]);
                }}
              >
                <input
                  type="email"
                  required
                  className="form-control d-inline-block me-2"
                  placeholder="Enter user email to grant access"
                  value={grantAlarmEmails[alarm.id] || ""}
                  onChange={(e) =>
                    setGrantAlarmEmails((prev) => ({
                      ...prev,
                      [alarm.id]: e.target.value,
                    }))
                  }
                  style={{ width: "300px" }}
                />
                <button
                  type="submit" 
                  className="btn btn-sm btn-outline-primary fs-5">
                  Grant Access
                </button>
              </form>

              {alarmAccess[alarm.id] && (
                  <div className="mt-3">
                    <p><strong>Authorized Users:</strong></p>
                    <ul>
                      {Object.keys(alarmAccess[alarm.id]).map((encodedEmail) => {
                        const email = decodeEmail(encodedEmail);
                        const isCurrentUser = email === user.email;
                        const targetRole = getUserRoleByEmail(email);
                        const isTargetAdmin = targetRole === Roles.ADMIN || targetRole === Roles.SUPER_ADMIN;
                        return (
                          <li key={encodedEmail}>
                            {email}
                            {!isCurrentUser && !isTargetAdmin? (
                              <button
                              type="button"
                              className="btn btn-sm btn-outline-danger ms-2"
                              onClick={() => revokeAlarmAccess(alarm.id, email)}
                            >
                              Revoke
                            </button>
                            ):(
                            <span className="text-muted ms-2">({isCurrentUser ? "You" : "Admin"})</span>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                )}
              </>
            )}
            </div>
            <div className="col-md-6 border-md-start ps-md-4 mt-3 mt-md-0">
              <p><strong>Logs:</strong></p>
              {logs[alarm.id]?.map((log, index) => (
                <p key={index}>
                  <em>{new Date(log.timestamp).toLocaleString()}:</em>
                  {log.message}</p>
              ))}
              <button
                className="btn btn-sm btn-outline-secondary me-2"
                onClick={() => {
                  const nextOffset = (logOffsets[alarm.id] || 0) + 3;
                  fetchLogs(alarm.id, "alarm", nextOffset);
                  setLogOffsets((prev) => ({ ...prev, [alarm.id]: nextOffset }));
                }}
              >
                Show more logs
              </button>
              <button
                className="btn btn-sm btn-outline-primary"
                onClick={() => refreshLogs(alarm.id, "alarm")}
              >
                Refresh Logs
              </button>
            </div>
            </div>
          </div>
          );}
        )}
      </div>
    )}
  </>
)}

    </div>
  );
}

export default App;
