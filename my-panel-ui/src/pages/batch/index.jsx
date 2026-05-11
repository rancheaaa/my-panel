import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const BatchTransferPage = () => {
  const navigate = useNavigate();
  
  useEffect(() => {
    navigate('batch/task-list');
  }, [navigate]);

  return null;
};

export default BatchTransferPage;
