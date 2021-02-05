from setuptools import setup

setup(
    name='jordan_py',
    version='0.1.0',
    author='Pierrick Baudet',
    author_email='pbaudet.enseirb@gmail.com',
    packages=['jordan_py', 'jordan_py.test'],
    license='LICENSE.txt',
    url='https://github.com/Mara-tech/jordan',
    description='A Python library easing use of Jordan in your Python code.',
    long_description=open('README.txt').read(),
    long_description_content_type='text/markdown',
    install_requires=[
        "requests",
    ],
)