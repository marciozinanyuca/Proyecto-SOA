import unittest
from unittest.mock import patch

from app import app


class OefaApiTests(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    @patch('app.requests.get')
    def test_oefa_uses_real_endpoint_and_surfaces_auth_error(self, mock_get):
        class FakeResponse:
            status_code = 403
            text = '{"status":403,"description":"Authentication credentials were not provided.","error":"NotAuthenticated","type":"api-error"}'

            def json(self):
                return {
                    "status": 403,
                    "description": "Authentication credentials were not provided.",
                    "error": "NotAuthenticated",
                    "type": "api-error",
                }

        mock_get.return_value = FakeResponse()

        response = self.client.get('/api/oefa')

        self.assertEqual(response.status_code, 403)
        self.assertIn('NotAuthenticated', response.get_json()['response']['error'])
        self.assertEqual(
            mock_get.call_args[0][0],
            'http://api.datosabiertos.oefa.gob.pe/api/v2/datastreams/'
        )


if __name__ == '__main__':
    unittest.main()
