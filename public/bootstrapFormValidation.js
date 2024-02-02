// Form validation using bootstrap
let formErrorsElement = $("#formErrors");

if (formErrorsElement.length > 0) {
let errors = JSON.parse(formErrorsElement.val());

for (const [key, [fieldError]] of Object.entries(errors)) {
    const input = document.querySelector(`input[name="${key}"], select[name="${key}"], input[name="${key}[]"], select[name="${key}[]"]`);

    if (input) {
        input.classList.add('is-invalid');

        const feedbackDiv = document.createElement('div');
        feedbackDiv.classList.add('invalid-feedback');
        feedbackDiv.innerHTML = fieldError;

        input.parentNode.appendChild(feedbackDiv);
    }
    }
}
