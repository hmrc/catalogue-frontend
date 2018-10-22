// The programming goals of Split.js are to deliver readable, understandable and
// maintainable code, while at the same time manually optimizing for tiny minified file size,
// browser compatibility without additional requirements, graceful fallback (IE8 is supported)
// and very few assumptions about the user's page layout.
const global = window;

// Save a couple long function names that are used frequently.
// This optimization saves around 400 bytes.
const gutterStartDragging = '_a';
const HORIZONTAL = 'horizontal';
const NOOP = () => false;

const Split = (idsOption, options = {}) => {
    let ids = idsOption;
    let clientAxis;
    let position;
    let elements;

    // Allow HTMLCollection to be used as an argument when supported
    if (Array.from) {
        ids = Array.from(ids)
    }

    const firstElement = document.querySelector(ids[0]);
    const parent = firstElement.parentNode;

    // Set default options.sizes to equal percentages of the parent element.
    const sizes = options.sizes || ids.map(() => 100 / ids.length);

    // Get other options
    const direction = options.direction || HORIZONTAL;
    const cursor = options.cursor = direction === HORIZONTAL ? 'ew-resize' : 'ns-resize';
    const elementStyle = options.elementStyle || (() => {});

    // 2. Initialize a bunch of strings based on the direction we're splitting.
    // A lot of the behavior in the rest of the library is paramatized down to
    // rely on CSS strings and classes.
    if (direction === HORIZONTAL) {
        clientAxis = 'clientX';
        position = 'left';
    } else if (direction === 'vertical') {
        clientAxis = 'clientY';
        position = 'top';
    }

    elements = ids.map((id, i) => {
        let pair;
        const element = {element: document.querySelector(id), size: sizes[i], i};

        if (i > 0) {
            pair = {a: i - 1, b: i, dragging: false, isFirst: (i === 1), isLast: (i === ids.length - 1), direction, parent};
            pair.gutter = element.element.previousElementSibling;

            // Save bound event listener for removal later
            pair[gutterStartDragging] = startDragging.bind(pair);
            pair.gutter.addEventListener('mousedown', pair[gutterStartDragging]);
            pair.gutter.addEventListener('touchstart', pair[gutterStartDragging]);
        }

        elementStyle(element.element, element.size);

        return element
    });

    function drag(e) {
        let offset;

        if (!this.dragging) {
            return
        }

        if ('touches' in e) {
            offset = e.touches[0][clientAxis] - this.start
        } else {
            offset = e[clientAxis] - this.start
        }

        const a = elements[this.a];
        elementStyle(a.element, offset);
    }


    // stopDragging is very similar to startDragging in reverse.
    function stopDragging() {
        const self = this;
        const a = elements[self.a].element;
        const b = elements[self.b].element;

        self.dragging = false;

        // Remove the stored event listeners. This is why we store them.
        global.removeEventListener('mouseup', self.stop);
        global.removeEventListener('touchend', self.stop);
        global.removeEventListener('touchcancel', self.stop);
        global.removeEventListener('mousemove', self.move);
        global.removeEventListener('touchmove', self.move);

        // Clear bound function references
        self.stop = null;
        self.move = null;

        a.removeEventListener('selectstart', NOOP);
        a.removeEventListener('dragstart', NOOP);
        b.removeEventListener('selectstart', NOOP);
        b.removeEventListener('dragstart', NOOP);

        a.style.userSelect = '';
        a.style.webkitUserSelect = '';
        a.style.MozUserSelect = '';
        a.style.pointerEvents = '';

        b.style.userSelect = '';
        b.style.webkitUserSelect = '';
        b.style.MozUserSelect = '';
        b.style.pointerEvents = '';

        self.gutter.style.cursor = '';
        self.parent.style.cursor = '';
        document.body.style.cursor = '';
    }

    // startDragging calls `calculateSizes` to store the inital size in the pair object.
    // It also adds event listeners for mouse/touch events,
    // and prevents selection while dragging so avoid the selecting text.
    function startDragging(e) {
        // Right-clicking can't start dragging.
        if (e.button !== 0) {
            return
        }

        // Alias frequently used variables to save space. 200 bytes.
        const self = this;
        const a = elements[self.a].element;
        const b = elements[self.b].element;

        // Don't actually drag the element. We emulate that in the drag function.
        e.preventDefault();

        // Set the dragging property of the pair object.
        self.dragging = true;

        // Create two event listeners bound to the same pair object and store
        // them in the pair object.
        self.move = drag.bind(self);
        self.stop = stopDragging.bind(self);

        // All the binding. `window` gets the stop events in case we drag out of the elements.
        global.addEventListener('mouseup', self.stop);
        global.addEventListener('touchend', self.stop);
        global.addEventListener('touchcancel', self.stop);
        global.addEventListener('mousemove', self.move);
        global.addEventListener('touchmove', self.move);

        // Disable selection. Disable!
        a.addEventListener('selectstart', NOOP);
        a.addEventListener('dragstart', NOOP);
        b.addEventListener('selectstart', NOOP);
        b.addEventListener('dragstart', NOOP);

        a.style.userSelect = 'none';
        a.style.webkitUserSelect = 'none';
        a.style.MozUserSelect = 'none';
        a.style.pointerEvents = 'none';

        b.style.userSelect = 'none';
        b.style.webkitUserSelect = 'none';
        b.style.MozUserSelect = 'none';
        b.style.pointerEvents = 'none';

        // Set the cursor at multiple levels
        self.gutter.style.cursor = cursor;
        self.parent.style.cursor = cursor;
        document.body.style.cursor = cursor;

        // Cache the initial sizes of the pair.
        // Figure out the parent size minus padding.
        const aBounds = a.getBoundingClientRect();
        self.start = aBounds[position]
    }
};

window.Split = Split;