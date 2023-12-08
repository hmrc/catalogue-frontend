$(function() { //document ready event

  // Controls the expand/collapse of the blog post section on the front page and the boards on repository pages
  $('.confirm-button').on('click',function(event) {
    event.preventDefault();
    let self = $(this);
    self.css("display", "block");
    self.addClass("mb-2");
    self.addClass("disabled");

    let confirmButton =$('<button class="btn btn-success me-2" type="submit">Confirm</button>');
    let cancelButton =$('<button class="btn btn-secondary">Cancel</button>');
    cancelButton.on('click', function(e) {
      e.preventDefault();
      confirmButton.remove();
      self.css("display", "inherit");
      self.removeClass("mb-2");
      self.removeClass("disabled");
      $(this).remove();  
    });

    self.parent().append(confirmButton).append(cancelButton).end();
  });
});
