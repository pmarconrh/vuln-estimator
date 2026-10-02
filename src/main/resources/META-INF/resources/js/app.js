// Small progressive-enhancement script for the upload page: drag & drop
// styling, selected-file chip list, and a basic client-side .zip filter.
// The server still re-validates everything, this is purely UX sugar.
(function () {
  const dropzone = document.getElementById("dropzone");
  const input = document.getElementById("file-input");
  const fileList = document.getElementById("file-list");
  const submitBtn = document.getElementById("upload-submit");

  if (!dropzone || !input) {
    return;
  }

  function renderChips(files) {
    fileList.innerHTML = "";
    let allZip = true;

    Array.from(files).forEach((file) => {
      const isZip = /\.zip$/i.test(file.name);
      if (!isZip) {
        allZip = false;
      }
      const chip = document.createElement("span");
      chip.className = "file-chip" + (isZip ? "" : " bg-danger-subtle text-danger");
      chip.innerHTML =
        '<i class="bi ' + (isZip ? "bi-file-earmark-zip" : "bi-exclamation-triangle") + '"></i> ' +
        file.name + " (" + formatSize(file.size) + ")";
      fileList.appendChild(chip);
    });

    if (submitBtn) {
      submitBtn.disabled = files.length === 0 || !allZip;
    }
  }

  function formatSize(bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  }

  dropzone.addEventListener("click", () => input.click());

  ["dragenter", "dragover"].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.add("dragover");
    })
  );

  ["dragleave", "drop"].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.remove("dragover");
    })
  );

  dropzone.addEventListener("drop", (e) => {
    const files = e.dataTransfer.files;
    input.files = files;
    renderChips(files);
  });

  input.addEventListener("change", () => renderChips(input.files));
})();
