(() => {
    const form = document.querySelector(".home-page .vehicle-search-form");
    if (!form) return;

    const category = form.querySelector("#categoryId");
    const model = form.querySelector("#modelId");
    if (!category || !model) return;

    function filterModels(resetInvalidSelection) {
        for (const option of model.options) {
            if (!option.value) continue;
            const allowed = !category.value || option.dataset.category === category.value;
            option.hidden = !allowed;
            option.disabled = !allowed;
        }

        // Preserve submitted criteria on initial render, including server errors.
        // Clear an incompatible model only when the user changes category.
        if (resetInvalidSelection && model.selectedOptions[0]?.disabled) {
            model.value = "";
        }
    }

    category.addEventListener("change", () => filterModels(true));
    window.addEventListener("pageshow", () => filterModels(false));
    filterModels(false);
})();
