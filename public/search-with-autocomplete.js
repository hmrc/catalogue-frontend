let form;
let inputSearch;
let matchesDiv;
let allValues = [];

const minSearch = 3;
let selectedIdx = -1; // Allow partial search
let matchesLen = 0;

const matchesListClass = "search-match-list";
const matchesElemId = "search-match-";
const matchSelectedClass = "search-match-selected";

function init(formId, inputSearchId, matchesDivId, values) {
    form = document.getElementById(formId);
    inputSearch = document.getElementById(inputSearchId);
    matchesDiv = document.getElementById(matchesDivId);
    allValues = values;

    inputSearch.addEventListener('keydown', disableDefaults, false);
    inputSearch.addEventListener('keyup', autoCompleteSearchInputListener, false)
}

function findMatches(rawInput) {
    let query = rawInput.trim();
    let matches = [];

    let terms = query.split(' ');
    let first = terms.shift();

    matches = allValues.filter(function (el) {
        return el.toLowerCase().includes(first.toLowerCase());
    });

    if(terms.length > 0) {
        terms.forEach(function(term) {
            matches = matches.filter(function (el) {
                return el.toLowerCase().includes(term.toLowerCase());
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
            }
            matchesList.appendChild(btn);
            idx++;
        });
    }

    matchesDiv.appendChild(matchesList);
    selectedIdx = -1;
    updateScroll();
    highlightMatch(selectedIdx, true);
    matchesDiv.classList.remove('hide');
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
    query.split(' ').forEach(function (term) {
        bolded = bolded.replaceAll(term, '<strong>' + term + '</strong>');
    });

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
    matchesDiv.classList.add('hide');
}

function disableDefaults(e) {
    if(e.keyCode === 38 || e.keyCode === 40 || e.keyCode === 13) {
        e.preventDefault();
    }
}

function autoCompleteSearchInputListener(e) {
    if(e.target.value.length >= minSearch) {
        if(e.keyCode > 46 || e.keyCode === 32 || e.keyCode === 8) { // alphanum, space and backspace
            clearMatches();
            findMatches(e.target.value);
        } else if (e.keyCode === 13) { //enter
            let selected = document.getElementById(matchesElemId + selectedIdx);
            if(selected != null) {
                selected.click();
            } else {
                form.submit();
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
        }
    } else {
        matchesDiv.innerHTML = '';
        matchesDiv.classList.add('hide');
    }
}
