(async () => {
  const data = { username: 'admin', password: 'admin123' };
  const res = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  console.log('status', res.status);
  const text = await res.text();
  console.log(text);
})();