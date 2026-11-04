// Renders answers as Markdown (sanitized), polls a running run, small form helpers.
// The stored answer text is never changed: the raw text stays in <pre class="answer-raw">.
(function () {
    'use strict';

    function renderAnswers(root) {
        if (!window.marked || !window.DOMPurify) {
            return; // CDN unavailable: raw text is shown
        }
        root.querySelectorAll('.answer-body').forEach(function (body) {
            if (body.dataset.rendered) {
                return;
            }
            var raw = body.querySelector('.answer-raw');
            var html = DOMPurify.sanitize(marked.parse(raw.textContent));
            var div = document.createElement('div');
            div.className = 'answer-rendered';
            div.innerHTML = html;
            raw.after(div);
            raw.hidden = true;
            if (window.hljs) {
                div.querySelectorAll('pre code').forEach(function (el) {
                    window.hljs.highlightElement(el);
                });
            }
            body.dataset.rendered = '1';
        });
    }

    document.addEventListener('click', function (e) {
        var toggle = e.target.closest('.toggle-raw');
        if (toggle) {
            var body = toggle.closest('.answer-body');
            var raw = body.querySelector('.answer-raw');
            var rendered = body.querySelector('.answer-rendered');
            if (rendered) {
                raw.hidden = !raw.hidden;
                rendered.hidden = !raw.hidden;
                toggle.textContent = raw.hidden ? 'исходный текст' : 'markdown';
            }
        }
        var copy = e.target.closest('.copy');
        if (copy && navigator.clipboard) {
            var text = copy.closest('.answer-body').querySelector('.answer-raw').textContent;
            navigator.clipboard.writeText(text).then(function () {
                copy.textContent = 'скопировано';
                setTimeout(function () { copy.textContent = 'копировать'; }, 1500);
            });
        }
    });

    function pollRun() {
        var live = document.getElementById('live');
        if (!live) {
            return;
        }
        var status = live.dataset.status;
        if (status !== 'QUEUED' && status !== 'RUNNING') {
            return;
        }
        setTimeout(function () {
            fetch(live.dataset.url, {headers: {'Accept': 'text/html'}})
                .then(function (r) { return r.ok ? r.text() : Promise.reject(r.status); })
                .then(function (html) {
                    var tmp = document.createElement('div');
                    tmp.innerHTML = html;
                    var fresh = tmp.querySelector('#live');
                    if (fresh) {
                        live.replaceWith(fresh);
                        renderAnswers(fresh);
                    }
                    pollRun();
                })
                .catch(function () { setTimeout(pollRun, 5000); });
        }, 2000);
    }

    function modelAdder() {
        var button = document.getElementById('add-model');
        var input = document.getElementById('extraModels');
        var list = document.getElementById('model-list');
        if (!button || !input || !list) {
            return;
        }
        button.addEventListener('click', function () {
            input.value.split(/[,\n]+/).map(function (s) { return s.trim(); }).filter(Boolean).forEach(function (m) {
                var exists = Array.prototype.some.call(list.querySelectorAll('input[name=models]'),
                    function (cb) { if (cb.value === m) { cb.checked = true; return true; } return false; });
                if (!exists) {
                    var label = document.createElement('label');
                    label.className = 'check';
                    var cb = document.createElement('input');
                    cb.type = 'checkbox';
                    cb.name = 'models';
                    cb.value = m;
                    cb.checked = true;
                    var span = document.createElement('span');
                    span.textContent = m;
                    label.append(cb, span);
                    list.append(label);
                }
            });
            input.value = '';
            input.focus();
        });
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                button.click();
            }
        });
    }

    // Run list: "select all", counter and the bulk delete button.
    function runSelection() {
        var form = document.getElementById('runs-form');
        if (!form) {
            return;
        }
        var all = document.getElementById('select-all');
        var button = document.getElementById('delete-selected');
        var counter = document.getElementById('selected-count');
        var checks = function () {
            return Array.prototype.filter.call(form.querySelectorAll('.run-check'), function (c) { return !c.disabled; });
        };
        var update = function () {
            var list = checks();
            var n = list.filter(function (c) { return c.checked; }).length;
            if (button) { button.disabled = n === 0; }
            if (counter) { counter.textContent = n ? 'отмечено: ' + n : ''; }
            if (all) {
                all.checked = n > 0 && n === list.length;
                all.indeterminate = n > 0 && n < list.length;
            }
        };
        if (all) {
            all.addEventListener('change', function () {
                checks().forEach(function (c) { c.checked = all.checked; });
                update();
            });
        }
        form.addEventListener('change', function (e) {
            if (e.target.classList.contains('run-check')) { update(); }
        });
        update();
    }

    // Any submit button with data-confirm asks first.
    document.addEventListener('click', function (e) {
        var button = e.target.closest('button[data-confirm]');
        if (button && !confirm(button.dataset.confirm)) {
            e.preventDefault();
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
        renderAnswers(document);
        pollRun();
        modelAdder();
        runSelection();
    });
})();
