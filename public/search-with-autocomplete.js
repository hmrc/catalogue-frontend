let form;
let inputSearch;
let matchesDiv;
let allValues = [];
let autoCompleteAllowPartial;
let autoCompleteIgnoreCase;
let autoCompleteSubmitOnSelection;

let _minSearch = 3;
let selectedIdx = -1; // Allow partial search
let matchesLen  = 0;

const matchesListClass   = "search-match-list";
const matchesElemId      = "search-match-";
const matchSelectedClass = "search-match-selected";

// Changed outside of script
function autoCompleteInit({formId, inputSearchId, matchesDivId, allowPartial, ignoreCase, values, submitOnSelection, minSearch}) {
    form        = document.forms[formId];
    inputSearch = document.getElementById(inputSearchId);
    matchesDiv  = document.getElementById(matchesDivId);
    allValues   = values;
    autoCompleteSubmitOnSelection = submitOnSelection != undefined && submitOnSelection;

    if (minSearch) { _minSearch = minSearch;}

    autoCompleteAllowPartial = allowPartial;
    if (allowPartial) {
        selectedIdx = -1;
        document.body.addEventListener("click", e => clearMatches(), true);
    }  else {
        selectedIdx = 0;
    }

    autoCompleteIgnoreCase = ignoreCase;

    inputSearch.addEventListener('keydown', disableDefaults, false);
    inputSearch.addEventListener('keyup', autoCompleteSearchInputListener, false);
    inputSearch.addEventListener('focus', autoCompleteSearchInputListener, false);
}

function findMatches(rawInput) {
    let query = rawInput.trim();
    let matches = [];

    let terms = query.split(' ');
    let first = terms.shift();

    matches = allValues.filter(function (el) {
        if (autoCompleteIgnoreCase) {
            return el.toLowerCase().includes(first.toLowerCase());
        } else {
            return el.includes(first);
        }
    });

    if(terms.length > 0) {
        terms.forEach(function(term) {
            matches = matches.filter(function (el) {
                if (autoCompleteIgnoreCase) {
                    return el.toLowerCase().includes(term.toLowerCase());
                } else {
                    return el.includes(term);
                }
            });
        });
    }

    let matchesList = document.createElement('div')
    matchesList.classList.add('list-group', matchesListClass);

    if(matches.length === 0) {
        let node = document.createElement('button');
        node.classList.add('list-group-item', 'disabled');
        node.innerHTML = 'No matches found';
        matchesList.appendChild(node);
    } else {
        let idx = 0
        matchesLen = matches.length;
        matches.forEach(function (match) {
            let btn = document.createElement('a');
            btn.id = matchesElemId + idx;
            btn.classList.add('list-group-item');
            btn.innerHTML = matchesInBold(query, match);
            btn.onclick = function() {
                clearMatches();
                inputSearch.value = match;
                if (autoCompleteSubmitOnSelection) {
                  submit();
                }
            }
            matchesList.appendChild(btn);
            idx++;
        });
    }

    matchesDiv.appendChild(matchesList);

    if (autoCompleteAllowPartial) {
        selectedIdx = -1;
    } else {
        selectedIdx = 0;
    }

    updateScroll();
    highlightMatch(selectedIdx, true);
    matchesDiv.classList.remove('d-none');
}

function highlightMatch(idx, toggle) {
    let match = document.getElementById(matchesElemId + idx)
    if(match != null) {
        if(toggle) {
            match.classList.add(matchSelectedClass);
        } else {
            match.classList.remove(matchSelectedClass);
        }
    }
}

function matchesInBold(query, match) {
    let bolded = match;

    if (autoCompleteIgnoreCase) {
        query.toLowerCase().split(' ').forEach(function (term) {
            bolded = bolded.toLowerCase().replaceAll(term, '<strong>' + term + '</strong>');
        });
    } else {
        query.split(' ').forEach(function (term) {
            bolded = bolded.replaceAll(term, '<strong>' + term + '</strong>');
        });
    }

    return bolded;
}

function updateScroll() {
    if(selectedIdx > 5) {
        const height = parseInt(window.getComputedStyle(document.getElementById(matchesElemId + '0')).height.replace('px', ''));
        matchesDiv.scrollTop = (selectedIdx - 5) * height;
    } else {
        matchesDiv.scrollTop = 0;
    }
}

function clearMatches() {
    matchesDiv.innerHTML = '';
    matchesDiv.classList.add('d-none');
}

function submit() {
  form.dispatchEvent(new Event("submit")); // Call form listener
  form.submit();
}

function disableDefaults(e) {
    if(e.keyCode === 38 || e.keyCode === 40 || e.keyCode === 13) {
        e.preventDefault();
    }
}

function autoCompleteSearchInputListener(e) {
    if(e.target.value.length >= _minSearch) {
        if(e.keyCode > 46 || e.keyCode === 32 || e.keyCode === 8) { // alphanum, space and backspace
            clearMatches();
            findMatches(e.target.value);
        } else if (e.keyCode === 13) { //enter
            let selected = document.getElementById(matchesElemId + selectedIdx);
            if(selected != null) {
                selected.click();
            } else {
                submit();
            }
        } else if (e.keyCode === 40) { //down arrow
            if(selectedIdx < matchesLen - 1) {
                highlightMatch(selectedIdx, false);
                selectedIdx++;
                highlightMatch(selectedIdx, true);
                updateScroll();
            }
        } else if (e.keyCode === 38) { //up arrow
            if(selectedIdx > -1) {
                highlightMatch(selectedIdx, false);
                selectedIdx--;
                highlightMatch(selectedIdx, true);
                updateScroll();
            }
        } else if (autoCompleteAllowPartial && e.keyCode === 27) { // escape
            clearMatches();
        }
    } else {
        matchesDiv.innerHTML = '';
        matchesDiv.classList.add('d-none');
    }
}
