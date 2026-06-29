(function () {
  function preferredTheme() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function storedTheme() {
    try {
      return localStorage.getItem('theme');
    } catch (e) {
      return null;
    }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    updateIcon(theme);
  }

  function saveTheme(theme) {
    try {
      localStorage.setItem('theme', theme);
    } catch (e) {
      // no-op
    }
  }

  function updateIcon(theme) {
    var icon = document.querySelector('#themeToggleIcon');
    if (!icon) {
      return;
    }
    icon.textContent = theme === 'dark' ? 'light_mode' : 'dark_mode';
  }

  function ensureToggle() {
    if (document.getElementById('themeToggleButton')) {
      updateIcon(document.documentElement.getAttribute('data-theme') || 'light');
      return;
    }

    var button = document.createElement('button');
    button.id = 'themeToggleButton';
    button.type = 'button';
    button.className = 'theme-toggle';
    button.setAttribute('aria-label', 'テーマ切替');
    button.innerHTML = '<span id="themeToggleIcon" class="material-icons">dark_mode</span>';

    button.addEventListener('click', function () {
      var current = document.documentElement.getAttribute('data-theme') || preferredTheme();
      var next = current === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      saveTheme(next);
    });

    document.body.appendChild(button);
    updateIcon(document.documentElement.getAttribute('data-theme') || preferredTheme());
  }

  document.addEventListener('DOMContentLoaded', function () {
    var theme = storedTheme() || preferredTheme();
    applyTheme(theme);
    ensureToggle();

    if (window.matchMedia) {
      var media = window.matchMedia('(prefers-color-scheme: dark)');
      media.addEventListener('change', function (e) {
        if (!storedTheme()) {
          applyTheme(e.matches ? 'dark' : 'light');
        }
      });
    }
  });
})();
