import { useState } from 'react';
import { useUpsertManagerReviewMutation } from '../store/api';
import { problemDetailMessage } from '../lib/problemDetail';

/** Matches the backend bound (ManagerReviewService.MAX_COMMENT_LENGTH). */
const MAX_COMMENT_LENGTH = 2000;

/**
 * The manager's review-write form (F-U6). A comment textarea plus submit that upserts
 * the single non-blocking review for a plan. Submit is disabled while the comment is
 * empty/whitespace or the write is in flight (no double-submit). The length is bounded
 * to match the backend with a live counter. On success a confirmation shows and the
 * cache invalidation refreshes the read-only {@code ManagerReviewNote} and the board's
 * review flag; on failure (e.g. 403) the ProblemDetail message shows and the text is
 * preserved.
 */
export default function ManagerReviewForm({ planId }: { planId: string }) {
  const [comment, setComment] = useState('');
  const [upsert, { isLoading, isError, error, isSuccess }] = useUpsertManagerReviewMutation();

  const disabled = comment.trim().length === 0 || isLoading;

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (disabled) {
      return;
    }
    void upsert({ planId, comment });
  };

  return (
    <form onSubmit={onSubmit} aria-label="Write manager review" className="space-y-2">
      <label htmlFor="review-comment" className="block text-sm font-medium text-gray-700">
        Write a review
      </label>
      <textarea
        id="review-comment"
        value={comment}
        maxLength={MAX_COMMENT_LENGTH}
        onChange={(e) => setComment(e.target.value)}
        rows={3}
        className="w-full rounded border border-gray-300 p-2 text-sm"
      />
      <div className="flex items-center justify-between">
        <span className="text-xs text-gray-400">
          {comment.length}/{MAX_COMMENT_LENGTH}
        </span>
        <button
          type="submit"
          disabled={disabled}
          className="rounded bg-indigo-600 px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
        >
          {isLoading ? 'Saving…' : 'Save review'}
        </button>
      </div>
      {isSuccess && (
        <p role="status" className="text-sm text-green-600">
          Review saved
        </p>
      )}
      {isError && (
        <p role="alert" className="text-sm text-red-600">
          {problemDetailMessage(error)}
        </p>
      )}
    </form>
  );
}
