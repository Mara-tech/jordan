from jordan_py import jordan
import unittest


class TestSimple(unittest.TestCase):

    def test_action_builder(self):
        actions = jordan \
            .with_action('shoot') \
            .with_parameter('player_name', jordan.PARAMETER_TYPE_STRING, default_value='Jordan') \
            .with_parameter('points', jordan.PARAMETER_TYPE_INT) \
            .build()
        self.assertIsNotNone(actions)
        self.assertEquals(len(actions), 1)
        self.assertEquals(actions[0]['actionName'], 'shoot')
        self.assertEquals(len(actions[0]['actionName']['parameters']), 2)
        self.assertEquals(actions[0]['actionName']['parameters'][0]['name'], 'player_name')
        self.assertEquals(actions[0]['actionName']['parameters'][0]['type'], 'string')
        self.assertEquals(actions[0]['actionName']['parameters'][0].get('defaultValue'), 'Jordan')
        self.assertEquals(actions[0]['actionName']['parameters'][1]['name'], 'points')
        self.assertEquals(actions[0]['actionName']['parameters'][1]['type'], 'int')
        self.assertIsNone(KeyError, actions[0]['actionName']['parameters'][0].get('defaultValue'))


if __name__ == '__main__':
    unittest.main()