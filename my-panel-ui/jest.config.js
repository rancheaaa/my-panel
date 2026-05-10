export default {
  testEnvironment: 'jsdom',
  transform: {
    '^.+\\.jsx?$': ['babel-jest', { 
      presets: [
        ['@babel/preset-env', { targets: { node: 'current' } }],
        '@babel/preset-react'
      ]
    }]
  },
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1'
  },
  moduleFileExtensions: ['js', 'jsx'],
  testMatch: ['**/__tests__/**/*.(test|spec).(js|jsx)'],
  collectCoverageFrom: [
    'src/pages/batch/**/*.{js,jsx}',
    'src/api/batch/**/*.js',
    '!src/pages/batch/**/__tests__/**',
    '!src/api/batch/**/__tests__/**'
  ],
  coverageDirectory: 'coverage',
  coverageReporters: ['text', 'lcov'],
  transformIgnorePatterns: [
    'node_modules/(?!(axios)/)'
  ]
};
