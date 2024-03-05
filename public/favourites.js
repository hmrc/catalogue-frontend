function UpdateListOnScreen(NewListItem) {
    var grabList = document.getElementById('requestList');

    var text = "" + GetCalendarName(NewListItem.calChoice) + " For " + GetLessonSlot(NewListItem.lessonChoice) + " On " + GetDateInTextForm(NewListItem.date) + "";
    var entry = document.createElement('li');
    entry.id = list.length - 1;
    entry.className = "ItemNotChecked";
    entry.appendChild(document.createTextNode(text));

    /*Add a button to each LI */
    var button = document.createElement('button');
    button.innerText = 'Click me!';
    entry.appendChild(button);

    grabList.appendChild(entry);
}