import http    from 'k6/http';
import { check, group, sleep } from 'k6';
import {
  BASE_URL,
  TEACHER_ID_MIN, TEACHER_ID_MAX,
  FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX,
} from '../config.js';
import { randomInt, randomCursor } from '../helpers/utils.js';
import { authHeader } from '../helpers/jwt.js';

const PAGE_SIZE = 20;

export function getFeedbackItems() {
  group('feedbacks', () => {
    const teacherId = randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX);
    const lastId    = randomCursor(FEEDBACK_ITEM_ID_MAX);
    const url       = `${BASE_URL}/feedbacks/items?teacherId=${teacherId}&size=${PAGE_SIZE}&lastId=${lastId}`;
    const res       = http.get(url, { tags: { name: 'GET /feedbacks/items' } });

    check(res, {
      'feedback items 200': (r) => r.status === 200,
    });

    sleep(randomInt(1, 2) * 0.1);
  });
}

export function getFeedbackItemDetail() {
  group('feedback_item_detail', () => {
    const itemId = randomInt(FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX);
    const res    = http.get(`${BASE_URL}/feedbacks/items/${itemId}`, { tags: { name: 'GET /feedbacks/items/{id}' } });

    check(res, {
      'feedback item detail 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    sleep(randomInt(1, 2) * 0.1);
  });
}

export function getMyFeedbacks(teacherToken) {
  group('feedbacks_auth', () => {
    const headers = authHeader(teacherToken);
    const lastId  = randomCursor(1_000_000);

    const myRes = http.get(
      `${BASE_URL}/feedbacks/my?size=${PAGE_SIZE}&lastId=${lastId}`,
      { headers, tags: { name: 'GET /feedbacks/my' } }
    );
    check(myRes, { 'my feedbacks 200': (r) => r.status === 200 });

    const myItemsRes = http.get(
      `${BASE_URL}/feedbacks/items/my?size=${PAGE_SIZE}&lastId=${lastId}`,
      { headers, tags: { name: 'GET /feedbacks/items/my' } }
    );
    check(myItemsRes, { 'my feedback items 200': (r) => r.status === 200 });

    sleep(randomInt(1, 3) * 0.1);
  });
}

export function getFeedbackTickets(token) {
  group('feedback_tickets', () => {
    const lastId = randomCursor(5_000_000);
    const res    = http.get(
      `${BASE_URL}/feedbacks/tickets?size=${PAGE_SIZE}&lastId=${lastId}`,
      { headers: authHeader(token), tags: { name: 'GET /feedbacks/tickets' } }
    );

    check(res, { 'feedback tickets 200': (r) => r.status === 200 });

    sleep(randomInt(1, 2) * 0.1);
  });
}
