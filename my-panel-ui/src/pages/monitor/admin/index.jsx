import React, { useEffect, useState } from 'react';
import { Card } from 'antd';

const AdminServer = () => {
  const [height, setHeight] = useState(document.documentElement.clientHeight - 94.5 + "px");

  useEffect(() => {
    const handleResize = () => {
        setHeight(document.documentElement.clientHeight - 94.5 + "px");
    };
    window.addEventListener("resize", handleResize);
    return () => {
        window.removeEventListener("resize", handleResize);
    };
  }, []);

  const url = import.meta.env.VITE_API_BASE_URL ? (import.meta.env.VITE_API_BASE_URL + '/admin/server/applications') : '/admin/server/applications';

  return (
    <div >
       <iframe 
         src={url} 
         frameBorder="no" 
         width="100%" 
         height={height} 
         scrolling="auto" 
         style={{ border: 0 }}
       />
    </div>
  );
};

export default AdminServer;
