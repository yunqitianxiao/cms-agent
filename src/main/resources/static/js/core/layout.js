const button = document.querySelector('.mobile-nav-toggle');
const sidebar = document.querySelector('.sidebar');
if (button && sidebar) {
  button.addEventListener('click', () => {
    const open = sidebar.classList.toggle('nav-open');
    button.setAttribute('aria-expanded', String(open));
    button.setAttribute('aria-label', open ? '收起导航' : '展开导航');
  });
  sidebar.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      sidebar.classList.remove('nav-open');
      button.setAttribute('aria-expanded', 'false');
      button.focus();
    }
  });
}
const main = document.querySelector('.main');
const content = document.querySelector('main');
if (main && content) {
  content.id = 'main-content';
  if (!main.querySelector('.skip-link')) {
    const link = document.createElement('a');
    link.className = 'skip-link';
    link.href = '#main-content';
    link.textContent = '跳到主要内容';
    main.prepend(link);
  }
}
document.querySelector('.nav-item.active')?.setAttribute('aria-current', 'page');
