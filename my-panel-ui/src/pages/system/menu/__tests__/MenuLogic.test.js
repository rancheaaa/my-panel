/**
 * Menu Logic Unit Test
 * 
 * Tests:
 * 1. findSiblings: correctly find siblings in a tree structure.
 * 2. Automatic sorting: max(siblings) + 1.
 */

const mockMenuData = [
  { menuId: 1, menuName: '首页', parentId: 0, orderNum: 1 },
  { 
    menuId: 2, 
    menuName: '系统管理', 
    parentId: 0, 
    orderNum: 2,
    children: [
      { menuId: 3, menuName: '用户管理', parentId: 2, orderNum: 1 },
      { menuId: 4, menuName: '角色管理', parentId: 2, orderNum: 2 },
    ]
  },
  { menuId: 10, menuName: '通知公告', parentId: 0, orderNum: 8 },
];

const findSiblings = (tree, menuId) => {
  for (const node of tree) {
    if (node.children && node.children.some(child => child.menuId === menuId)) {
      return node.children;
    }
    if (node.children) {
      const result = findSiblings(node.children, menuId);
      if (result) return result;
    }
  }
  if (tree.some(node => node.menuId === menuId)) {
    return tree;
  }
  return null;
};

// Test Case 1: Find siblings of a root node
const rootSiblings = findSiblings(mockMenuData, 1);
console.assert(rootSiblings.length === 3, 'Root siblings should have 3 items');
console.assert(rootSiblings.some(n => n.menuId === 10), 'Root siblings should contain node 10');

// Test Case 2: Find siblings of a child node
const childSiblings = findSiblings(mockMenuData, 3);
console.assert(childSiblings.length === 2, 'Child siblings should have 2 items');
console.assert(childSiblings.some(n => n.menuId === 4), 'Child siblings should contain node 4');

// Test Case 3: Automatic sorting logic
const calculateNextOrder = (siblings) => {
  if (!siblings || siblings.length === 0) return 1;
  return Math.max(...siblings.map(item => item.orderNum || 0)) + 1;
};

const nextOrderRoot = calculateNextOrder(rootSiblings);
console.assert(nextOrderRoot === 9, 'Next order for root should be 9 (8+1)');

const nextOrderChild = calculateNextOrder(childSiblings);
console.assert(nextOrderChild === 3, 'Next order for child should be 3 (2+1)');

console.log('All menu logic unit tests passed!');
