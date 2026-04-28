import md5 from 'js-md5';

export function encryptPassword(password, salt) {
  if (!salt) {
    return md5(password);
  }
  return md5(md5(password) + salt);
}

export { md5 };