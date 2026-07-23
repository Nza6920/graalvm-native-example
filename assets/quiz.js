document.querySelectorAll('[data-quiz]').forEach((quiz) => {
  const feedback = quiz.querySelector('.feedback');
  quiz.querySelectorAll('button').forEach((button) => {
    button.addEventListener('click', () => {
      const correct = button.dataset.correct === 'true';
      feedback.textContent = correct
        ? quiz.dataset.correctFeedback || '正确：Native Image 必须在构建期确定可达代码。'
        : quiz.dataset.incorrectFeedback || '再想一下：原生可执行文件运行时不能再补进未知代码。';
    });
  });
});
