const { test, expect } = require('@playwright/test');

test.describe('Menu Management Drag and Drop E2E', () => {
  test.beforeEach(async ({ page }) => {
    // Login before each test
    await page.goto('/login');
    await page.fill('input[name="username"]', 'admin');
    await page.fill('input[name="password"]', 'admin123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/home/index');
    await page.goto('/system/menu');
  });

  test('Drag and drop to reorder menu items', async ({ page }) => {
    // 1. Wait for table to load
    await page.waitForSelector('.ant-table-row');
    
    // 2. Get the first two rows (root items)
    const rows = await page.locator('.ant-table-row').all();
    const firstRowId = await rows[0].getAttribute('data-row-key');
    const secondRowId = await rows[1].getAttribute('data-row-key');
    
    // 3. Drag the first row handle to the second row
    const dragHandle = page.locator('.anticon-holder').first();
    const targetRow = rows[1];
    
    await dragHandle.hover();
    await page.mouse.down();
    await targetRow.hover();
    await page.mouse.up();
    
    // 4. Verify loading overlay
    await expect(page.locator('.ant-spin-spinning')).toBeVisible();
    
    // 5. Verify toast message on success
    await expect(page.locator('.ant-message-success')).toContainText('排序更新成功');
    
    // 6. Verify data refresh and new order
    await page.waitForSelector('.ant-table-row');
    const newRows = await page.locator('.ant-table-row').all();
    const newFirstRowId = await newRows[0].getAttribute('data-row-key');
    expect(newFirstRowId).toBe(secondRowId);
  });

  test('Add menu with auto sort order', async ({ page }) => {
    // 1. Click Add button
    await page.click('button:has-text("新增")');
    
    // 2. Verify orderNum field is disabled and has hint
    const orderNumInput = page.locator('input#orderNum');
    await expect(orderNumInput).toBeDisabled();
    
    const hint = page.locator('.ant-form-item-label >> text=显示排序');
    await expect(hint).toContainText('(系统自动生成)');
    
    // 3. Get current max order (mock-based check or just verify input has value > 0)
    const currentValue = await orderNumInput.inputValue();
    expect(parseInt(currentValue)).toBeGreaterThan(0);
    
    // 4. Fill other required fields
    await page.fill('input#menuName', 'Test New Menu');
    await page.fill('input#path', 'test-path');
    
    // 5. Save and verify
    await page.click('button:has-text("确 定")');
    await expect(page.locator('.ant-message-success')).toContainText('新增成功');
  });
});
