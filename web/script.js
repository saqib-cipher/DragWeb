(function () {
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---- back btn: go back to main portfolio page ---- */
  var backBtn = document.getElementById('backBtn');
  if (backBtn) {
    backBtn.addEventListener('click', function () {
      window.location.href = '../index.html';
    });
  }

  /* ---- global navigation button toggle ---- */
  window.toggleActive = function (clickedButton) {
    var group = clickedButton.parentElement;
    var buttons = group.querySelectorAll('.expressive-btn');
    buttons.forEach(function (btn) { btn.classList.remove('active'); });
    clickedButton.classList.add('active');
  };

  /* ---- tab scrollspy ---- */
  var tabs = Array.prototype.slice.call(document.querySelectorAll('.expressive-btn'));
  var sections = tabs.map(function (t) {
    return document.getElementById(t.getAttribute('href').slice(1));
  });

  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        var idx = sections.indexOf(entry.target);
        if (idx === -1 || !entry.isIntersecting) return;
        tabs.forEach(function (t) { t.classList.remove('active'); });
        tabs[idx].classList.add('active');
        tabs[idx].scrollIntoView({ block: 'nearest', inline: 'center', behavior: reduced ? 'auto' : 'smooth' });
      });
    }, { rootMargin: '-45% 0px -50% 0px', threshold: 0 });
    sections.forEach(function (s) { if (s) io.observe(s); });
  }

  /* ---- generate code panel ---- */
  var genBtn = document.getElementById('genBtn');
  var genPanel = document.getElementById('genPanel');
  if (genBtn && genPanel) {
    genBtn.addEventListener('click', function () {
      var open = genPanel.classList.toggle('open');
      genBtn.textContent = open ? '⚙ Hide code' : '⚙ Generate code';
    });
  }

  /* ---- expand all: open every nested block + the code panel ---- */
  var expandBtn = document.getElementById('expandBtn');
  if (expandBtn) {
    expandBtn.addEventListener('click', function () {
      document.querySelectorAll('.gen-panel').forEach(function (p) { p.classList.add('open'); });
      if (genBtn) genBtn.textContent = '⚙ Hide code';
      document.querySelectorAll('.doc-section').forEach(function (s) {
        s.scrollIntoView; // no-op placeholder, sections are already always visible
      });
      window.scrollTo({ top: document.getElementById('tags').offsetTop - 160, behavior: reduced ? 'auto' : 'smooth' });
    });
  }

  /* ---- copy link ---- */
  var copyBtn = document.getElementById('copyLinkBtn');
  if (copyBtn) {
    copyBtn.addEventListener('click', function () {
      var url = window.location.href.split('#')[0];
      var active = document.querySelector('.tab.active');
      var full = active ? url + active.getAttribute('href') : url;
      var original = copyBtn.textContent;
      var done = function () {
        copyBtn.textContent = '✓ Copied';
        setTimeout(function () { copyBtn.textContent = original; }, 1400);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(full).then(done).catch(function () { done(); });
      } else {
        done();
      }
    });
  }

  /* ========================================================
     INTERACTIVE SANDBOX LOGIC
     ======================================================== */
  var sbWidgetType = document.getElementById('sbWidgetType');
  var sbText = document.getElementById('sbText');
  var sbPadding = document.getElementById('sbPadding');
  var sbRadius = document.getElementById('sbRadius');
  var sbFontSize = document.getElementById('sbFontSize');
  var sbBgColor = document.getElementById('sbBgColor');
  var sbEnableClick = document.getElementById('sbEnableClick');

  var valPadding = document.getElementById('valPadding');
  var valRadius = document.getElementById('valRadius');
  var valFontSize = document.getElementById('valFontSize');

  var previewWrapper = document.getElementById('sandboxPreviewWrapper');
  var codeOutput = document.getElementById('codeOutput');
  var clickCountVal = document.getElementById('clickCountVal');
  
  var clickCount = 0;
  var currentTab = 'html';

  // Code tabs
  var codeTabs = document.querySelectorAll('.code-tab');
  codeTabs.forEach(function (tab) {
    tab.addEventListener('click', function () {
      codeTabs.forEach(function (t) { t.classList.remove('active'); });
      tab.classList.add('active');
      currentTab = tab.getAttribute('data-tab');
      updateSandbox();
    });
  });

  function getHexColor(val) {
    if (val === 'var(--primary)') return '#aaccff';
    if (val === 'var(--green)') return '#34a853';
    if (val === 'var(--atv-btn)') return '#284777';
    return val;
  }

  function getTextColor(bgColor) {
    if (bgColor === 'transparent') return 'var(--body-text)';
    if (bgColor === 'var(--primary)') return '#0c1a30';
    if (bgColor === 'var(--green)') return '#07240c';
    return '#ffffff';
  }

  function updateSandbox() {
    if (!previewWrapper || !codeOutput) return;

    var type = sbWidgetType.value;
    var textVal = sbText.value;
    var padVal = sbPadding.value;
    var radVal = sbRadius.value;
    var fontVal = sbFontSize.value;
    var bgVal = sbBgColor.value;
    var isClickEnabled = sbEnableClick.checked;

    // Toggle yellow logic block opacity based on checkbox state
    var m3LogicBlock = document.getElementById('m3LogicBlock');
    if (m3LogicBlock) {
      if (isClickEnabled) {
        m3LogicBlock.classList.remove('disabled');
      } else {
        m3LogicBlock.classList.add('disabled');
      }
    }

    // 1. Re-render visual preview element
    previewWrapper.innerHTML = '';
    var el;
    if (type === 'button') {
      el = document.createElement('button');
      el.className = 'styled-widget widget-button';
    } else if (type === 'text') {
      el = document.createElement('p');
      el.className = 'styled-widget widget-text';
    } else {
      el = document.createElement('div');
      el.className = 'styled-widget widget-card';
    }

    el.id = 'myWidget';
    el.textContent = textVal;
    el.style.padding = padVal + 'px';
    el.style.borderRadius = radVal + 'px';
    el.style.fontSize = fontVal + 'px';
    el.style.backgroundColor = bgVal;
    el.style.color = getTextColor(bgVal);

    if (isClickEnabled) {
      el.classList.add('clickable');
      el.addEventListener('click', function () {
        clickCount++;
        if (clickCountVal) clickCountVal.textContent = clickCount;
        
        // Dynamic scale flash animation on click
        el.style.transform = 'scale(0.92)';
        setTimeout(function() {
          el.style.transform = '';
        }, 120);
      });
    }

    previewWrapper.appendChild(el);

    // 2. Generate code view strings
    var codeText = '';
    var hexColor = getHexColor(bgVal);
    var textClr = getTextColor(bgVal);

    if (currentTab === 'html') {
      if (type === 'button') {
        codeText = '<button id="myWidget" class="styled-btn">' + textVal + '</button>';
      } else if (type === 'text') {
        codeText = '<p id="myWidget" class="styled-text">' + textVal + '</p>';
      } else {
        codeText = '<div id="myWidget" class="styled-card">\n  ' + textVal + '\n</div>';
      }
    } else if (currentTab === 'css') {
      codeText = '#myWidget {\n' +
                 '  background-color: ' + hexColor + ';\n' +
                 '  color: ' + (textClr.startsWith('var') ? '#e2e2e9' : textClr) + ';\n' +
                 '  padding: ' + padVal + 'px;\n' +
                 '  border-radius: ' + radVal + 'px;\n' +
                 '  font-size: ' + fontVal + 'px;\n' +
                 '  font-family: \'Quicksand\', sans-serif;\n';
      if (type === 'button') {
        codeText += '  border: 1px solid rgba(255, 255, 255, 0.1);\n' +
                    '  cursor: pointer;\n' +
                    '  transition: transform 0.2s;\n' +
                    '}\n\n' +
                    '#myWidget:active {\n' +
                    '  transform: scale(0.95);\n' +
                    '}';
      } else if (type === 'text') {
        codeText += '  margin: 0;\n}';
      } else {
        codeText += '  border: 1px solid #44474f;\n}';
      }
    } else if (currentTab === 'js') {
      if (isClickEnabled) {
        codeText = 'const myWidget = document.getElementById(\'myWidget\');\n' +
                   'let clickCount = 0;\n\n' +
                   '// Setup click listener (onClick Event Block)\n' +
                   'myWidget.addEventListener(\'click\', () => {\n' +
                   '  clickCount++;\n' +
                   '  console.log(\'Widget clicked! Count:\', clickCount);\n' +
                   '  \n' +
                   '  // Trigger bounce animation\n' +
                   '  myWidget.style.transform = \'scale(0.92)\';\n' +
                   '  setTimeout(() => myWidget.style.transform = \'\', 120);\n' +
                   '});';
      } else {
        codeText = '// No event listeners added.\n// Enable the onClick toggle on the left to generate logic code.';
      }
    }

    codeOutput.textContent = codeText;
  }

  // Bind input listeners
  [sbWidgetType, sbBgColor].forEach(function (sel) {
    if (sel) sel.addEventListener('change', updateSandbox);
  });
  
  if (sbText) sbText.addEventListener('input', updateSandbox);
  
  [sbPadding, sbRadius, sbFontSize].forEach(function (slider) {
    if (slider) slider.addEventListener('input', updateSandbox);
  });

  if (sbEnableClick) {
    sbEnableClick.addEventListener('change', function() {
      clickCount = 0;
      if (clickCountVal) clickCountVal.textContent = '0';
      updateSandbox();
    });
  }

  /* ---- CSS Box Model Sliders ---- */
  var mSlider = document.getElementById('mSlider');
  var pSlider = document.getElementById('pSlider');
  var mVal = document.getElementById('mVal');
  var pVal = document.getElementById('pVal');
  var bmMargin = document.getElementById('bmMargin');
  var bmPadding = document.getElementById('bmPadding');

  if (mSlider && bmMargin && mVal) {
    mSlider.addEventListener('input', function () {
      bmMargin.style.setProperty('--m', mSlider.value + 'px');
      mVal.textContent = mSlider.value + 'px';
    });
  }
  if (pSlider && bmPadding && pVal) {
    pSlider.addEventListener('input', function () {
      bmPadding.style.setProperty('--p', pSlider.value + 'px');
      pVal.textContent = pSlider.value + 'px';
    });
  }

  /* ---- CSS Box Model Border Toggle ---- */
  var borderToggle = document.getElementById('borderToggle');
  var bmBorder = document.getElementById('bmBorder');
  if (borderToggle && bmBorder) {
    borderToggle.checked = false;
    bmBorder.classList.add('disabled');
    borderToggle.addEventListener('change', function () {
      if (borderToggle.checked) {
        bmBorder.classList.remove('disabled');
      } else {
        bmBorder.classList.add('disabled');
      }
    });
  }

  // Initial load
  updateSandbox();
})();
