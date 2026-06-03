import { describe, expect, it } from 'vitest';
import { problemDetailMessage } from '../lib/problemDetail';

describe('problemDetailMessage', () => {
  it('returns the ProblemDetail `detail` when present', () => {
    const error = {
      status: 409,
      data: {
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'Plan is already locked',
      },
    };
    expect(problemDetailMessage(error)).toBe('Plan is already locked');
  });

  it('falls back to `title` when there is no `detail`', () => {
    const error = {
      status: 422,
      data: {
        type: 'about:blank',
        title: 'Unprocessable Entity',
        status: 422,
      },
    };
    expect(problemDetailMessage(error)).toBe('Unprocessable Entity');
  });

  it('falls back to a generic message for a non-ProblemDetail body', () => {
    const error = { status: 500, data: { error: 'boom' } };
    expect(problemDetailMessage(error)).toBe('Something went wrong');
  });

  it('falls back to a generic message for an empty/missing data body', () => {
    expect(problemDetailMessage({ status: 500 })).toBe('Something went wrong');
    expect(problemDetailMessage({ status: 500, data: null })).toBe('Something went wrong');
  });

  it('falls back to a generic message for a network/parsing error (FETCH_ERROR)', () => {
    const error = { status: 'FETCH_ERROR', error: 'TypeError: Failed to fetch' };
    expect(problemDetailMessage(error)).toBe('Something went wrong');
  });

  it('tolerates undefined, null, and string errors', () => {
    expect(problemDetailMessage(undefined)).toBe('Something went wrong');
    expect(problemDetailMessage(null)).toBe('Something went wrong');
    expect(problemDetailMessage('nope')).toBe('Something went wrong');
  });
});
