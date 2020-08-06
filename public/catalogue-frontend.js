$(function() { //document ready event

  // Controls the expand/collapse of the what's new and blog post sections on the front page
  $('.recent-box-expand-link').on('click',function() {
    var growDiv = $(this).siblings('.recent-box-grow').first();
    var wrapper = growDiv.find(".recent-box-collapse");
    var link = growDiv.siblings('.recent-box-expand-link').first();

    var smallHeight = 120; //Should match css value

    if (growDiv.height() > smallHeight) {
      growDiv.css("height",smallHeight + "px"); //Collapse to small height
      link.text('See all...');
    } else {
      growDiv.css("height",wrapper.innerHeight() + "px"); //Expand to height of inner content
      link.text('...Collapse');
    }

  });

});