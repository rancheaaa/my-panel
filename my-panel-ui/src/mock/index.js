import Mock from 'mockjs';

import './mockAuth';
import './mockUser';
import './mockRole';
import './mockDict';
import './mockMonitor';
import './mockConfig';

Mock.setup({
  timeout: '200-600',
});

console.log('Mock Data Loaded');
