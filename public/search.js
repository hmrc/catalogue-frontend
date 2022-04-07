// Drop Down Search


let bar = document.getElementById("catalogue-search-bar")
let input = document.getElementById("catalogue-search")
let matches = document.getElementById("catalogue-search-matches")
let selectedItem = 0

const minSearchLen = 3

input.onkeyup = e => {
    if (e.target.value.length >= minSearchLen) {
        matches.classList.remove("hide")
        if (e.keyCode > 46 || e.keyCode === 32) { // exclude arrow keys
            search(e.target.value)
        }
    } else {
        matches.innerHTML = "";
        matches.classList.add("hide")
    }
}

// Prevent search firing off until user stops typing
let debounceTimer

function search(q) {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
        let oReq = new XMLHttpRequest();
        oReq.onreadystatechange = function () {
            if (this.readyState == 4 && this.status == 200) {
                matches.innerHTML = oReq.responseText;
                selectedItem = 0
                highlight(0)
            }
        };
        oReq.open("GET", "/quicksearch?query=" + encodeURIComponent(q));
        oReq.send();
    }, 250)

}

// show and hide the whole search bar
function toggleSearch() {
    if (bar.classList.contains("hide")) {
        showSearchBar()
    } else {
        hideSearchBar()
    }
}

function showSearchBar() {
    bar.classList.remove("hide")
    input.focus()
    input.value = ""
}

function hideSearchBar() {
    bar.classList.add("hide")
    matches.classList.add("hide")
    input.value = ""
    selectedItem = -1
    matches.innerHTML = ""
}

// hides the drop down part of the search
function clearAutoComplete() {
    matches.classList.add("hide")
    input.value = ""
    selectedItem = -1
    matches.innerHTML = ""
}

function globalSearchShortcut(e) {
    if (e.ctrlKey && e.key === " ") {
        toggleSearch();
    }
}

function searchInputListener(e) {
    if (e.keyCode === 13) { // enter opens selected item
        // get the first element from the list
        let firstItem = document.getElementById("search-item-" + selectedItem)
        if (firstItem != null) {
            firstItem.click();
        }
    } else if (e.keyCode === 38) { // item select, move up arrow
        if (selectedItem > 0) {
            unhighlight(selectedItem)
            selectedItem--
            highlight(selectedItem)
        }
    } else if (e.keyCode === 40) { // item select move down arrow
        unhighlight(selectedItem)
        selectedItem++
        highlight(selectedItem)
    } else if (e.keyCode === 27) { // escape
        clearAutoComplete()
    } else if (e.keyCode > 40 || e.keyCode < 33) { // trigger search
        if (e.target.value.length >= minSearchLen) {
            matches.classList.remove("hide")
            search(e.target.value)
        } else {
            matches.innerHTML = "";
            matches.classList.add("hide")
        }
    }
}

// prevent the up and down arrows changing the cursor position in the input box
function disableArrowKeys(e) {
    switch (e.key) {
        case "ArrowUp":
        case "ArrowDown":
            e.preventDefault();
            break;
    }
}

function highlight(pos) {
    let item = document.getElementById("search-item-" + pos)
    if (item != null) {
        item.parentElement.parentElement.classList.add("search-item-selected")
    }
}

function unhighlight(pos) {
    let item = document.getElementById("search-item-" + pos)
    if (item != null) {
        item.parentElement.parentElement.classList.remove("search-item-selected")
    }
}

document.getElementById("searchicon").addEventListener("click", function (e) {
    e.stopImmediatePropagation();
    toggleSearch();
}, true)
input.addEventListener("keyup", searchInputListener, false)
input.addEventListener("keydown", disableArrowKeys, false)
document.getElementById("standard-layout-container").addEventListener("click", clearAutoComplete, true)
document.addEventListener("keyup", globalSearchShortcut, false)
