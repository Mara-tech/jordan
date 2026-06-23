from jordan_py import jordan
import unittest


class TestSimple(unittest.TestCase):

    def test_action_builder(self):
        actions = (jordan
                   .with_action('shoot')
                   .with_parameter('player_name', jordan.PARAMETER_TYPE_STRING, default_value='Jordan')
                   .with_parameter('points', jordan.PARAMETER_TYPE_INT)
                   .build())
        self.assertIsNotNone(actions)
        self.assertEqual(len(actions), 1)
        self.assertEqual(actions[0]['actionName'], 'shoot')
        params = actions[0]['parameters']
        self.assertEqual(len(params), 2)
        self.assertEqual(params[0]['name'], 'player_name')
        self.assertEqual(params[0]['type'], 'string')
        self.assertEqual(params[0].get('defaultValue'), 'Jordan')
        self.assertEqual(params[1]['name'], 'points')
        self.assertEqual(params[1]['type'], 'int')
        self.assertIsNone(params[1].get('defaultValue'))


if __name__ == '__main__':
    unittest.main()
